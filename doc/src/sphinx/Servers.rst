.. _finagle_servers:

Servers
=======

Finagle servers implement a simple :src:`interface: <com/twitter/finagle/Server.scala>`

.. code-block:: scala

  def serve(
    addr: SocketAddress,
    factory: ServiceFactory[Req, Rep]
  ): ListeningServer

When given a ``SocketAddress`` and a :ref:`ServiceFactory <service_factory>`, a server returns a
``ListeningServer``. The ``ListeningServer`` allows for management of server resources. The interface
comes with variants that allow for serving a simple ``Service`` as well. Typical usage takes the form
``Protocol.serve(...)``, for example:

.. code-block:: scala

  import com.twitter.finagle.Service
  import com.twitter.finagle.Http
  import com.twitter.finagle.http.{Request, Response}
  import com.twitter.util.{Await, Future}

  val service: Service[Request, Response] = new Service[Request, Response] {
    def apply(req: Request): Future[Response] = Future.value(Response())
  }

  val server = Http.server.serve(":8080", service)
  Await.ready(server) // waits until the server resources are released

.. note:: `finagle-thrift` servers expose a rich API because their interfaces are defined
          via a Thrift IDL. See the protocols section on :ref:`Thrift <thrift_and_scrooge>`
          for more details.

Server Modules
--------------

Finagle servers are simple; they are designed to serve requests quickly. As such,
Finagle minimally furnishes servers with additional behavior. More sophisticated
behavior lives in the :ref:`clients <finagle_clients>`.

.. figure:: _static/serverstack.svg

    Fig. 1: A visual representation of each module in a default Finagle server. Requests flow from left to right.

Many of the server modules act as `admission controllers` that make a decision (based on either a dynamic or
static property) whether this server can handle the incoming request while maintaining some SLO (Service Level
Objective).

.. note:: An `Endpoint` module represents a user-defined ``Service`` passed to the ``Server.serve(...)``
          method.

Observability
^^^^^^^^^^^^^

A Finagle server comes with some useful modules to help owners observe and debug
their servers. This includes :src:`Monitoring <com/twitter/finagle/filter/MonitorFilter.scala>`,
:src:`Tracing <com/twitter/finagle/tracing/TraceInitializerFilter.scala>`,
and :src:`Stats <com/twitter/finagle/service/StatsFilter.scala>`.

All the unhandled exceptions from a user-defined service flow through the ``Monitor`` object
used by the given Finagle server. See :ref:`this example <configuring_monitors>` on how to
override the default instance that simply logs exceptions onto a the standard output.

.. include:: shared-modules/ResponseClassification.rst

Concurrency Limit
^^^^^^^^^^^^^^^^^

The `Concurrency Limit` module is implemented by
:src:`RequestSemaphoreFilter <com/twitter/finagle/filter/RequestSemaphoreFilter.scala>` and maintains
the `concurrency` of the Finagle server.

By default, this module is disabled, which means a Finagle server's requests concurrency is unbounded.
To enable the `Concurrency Limit` module and put some bounds in terms of maximum number of requests
that might be handled concurrently by your server, use the following example [#example]_.

.. code-block:: scala

  import com.twitter.finagle.Http

  val server = Http.server
    .withAdmissionControl.concurrencyLimit(
      maxConcurrentRequests = 10,
      maxWaiters = 0
    )
    .serve(":8080", service)

The `Concurrency Limit` module is configured with two parameters:

1. `maxConcurrentRequests` - the number of requests allowed to be handled concurrently
2. `maxWaiters` - the number of requests (on top of `maxConcurrentRequests`) allowed to be queued

All the incoming requests on top of ``(maxConcurrentRequests + maxWaiters)`` will be
`rejected` [#nack]_ by the server. That said, the `Concurrency Limit` module acts as
`static admission controller` monitoring the current concurrency level of the incoming requests.

See :ref:`Requests Concurrency metrics <requests_concurrency_limit>` for more details.

Request Deadline
^^^^^^^^^^^^^^^^

The `Request Deadline` module acts as deadline driven server-side `static admission controller` and
`rejects` [#nack]_ all the incoming requests that expire their deadline. Generally, the deadline for
each request is set up by a frontend service and propagated over the wire to the downstream services.

The `Request Deadline` module is implemented by
:src:`DeadlineFilter <com/twitter/finagle/filter/DeadlineFilter.scala>` and configured with
following params [#example]_.

.. code-block:: scala

  import com.twitter.conversions.time._
  import com.twitter.finagle.Http

  val server = Http.server
    .withAdmissionControl.deadlineTolerance(200.milliseconds)
    .withAdmissionControl.deadlineMaxRejectedPercentage(0.3)
    .serve(":8080", service)

There are two arguments passed to the `Request Deadline` module, which might be configured
separately:

1. `deadlineTolerance` (default: 170 ms) - the maximum elapsed time since a request's deadline
   when it will be considered for rejection
2. `deadlineMaxRejectedPercentage` (default: 20%) - the maximum percentage of requests that can be
   rejected

See :ref:`Deadline Admission Control metrics <deadline_admission_control_stats>` for more details.

.. note:: The `Request Deadline` module is currently in an experimental mode where it doesn't actually
          reject any of the `expired` requests, but only maintains metrics.

Request Timeout
^^^^^^^^^^^^^^^

The `Request Timeout` module is implemented by
:src:`TimeoutFilter <com/twitter/finagle/service/TimeoutFilter.scala>` and simply `fails` all the
requests that a given server hasn't be able to handle in the given amount of time. As well as for
Finagle clients, this module is disabled by default (the timeout is unbounded). See
:ref:`this example <configuring_timeouts>` to override this behaviour.

.. note:: The `Request Timeout` module doesn't `reject` the incoming request, but `fails` it. This
          means it won't by default be retried by a remote client given it's not known whether
          the request has been timed out being in the queue (waiting for processing) or being
          processed.

.. rubric:: Footnotes

.. [#nack] Depending on the protocol, a rejected request might be transformed into a `NACK`
   (currently supported in HTTP/1.1 and Mux) so the remote client can safely `retry` it.

.. [#example] Configuration parameters/values provided in this example are only demonstrate
   the API usage, not the real world values. We do not recommend blindly applying those values
   to production systems.
