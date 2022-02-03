package com.softwareverde.http.server.servlet;

import com.softwareverde.bitcoin.server.module.stratum.BitcoinCoreStratumServer;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubscribeResponseMessage;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SubscribeApiServlet implements Servlet {
    protected final BitcoinCoreStratumServer _stratumServer;

    protected final AtomicLong _minerIdGenerator = new AtomicLong(0L);
    protected final ConcurrentHashMap<ByteArray, Long> _subscriptionMinerIds;

    public SubscribeApiServlet(final BitcoinCoreStratumServer stratumServer, final ConcurrentHashMap<ByteArray, Long> subscriptionMinerIds) {
        _stratumServer = stratumServer;
        _subscriptionMinerIds = subscriptionMinerIds;
    }

    @Override
    public Response onRequest(final Request request) {
        if (request.getMethod() != HttpMethod.GET) {
            return ServletUtil.createErrorResponse(Response.Codes.BAD_REQUEST, "Bad request.");
        }

        final Long minerId = _minerIdGenerator.incrementAndGet();
        final MinerSubscribeResponseMessage minerSubscribeResponseMessage = _stratumServer.subscribeMiner(minerId);
        if (minerSubscribeResponseMessage == null) {
            return ServletUtil.createErrorResponse(Response.Codes.SERVER_ERROR, "Unable to subscribe miner.");
        }

        final ByteArray subscriptionId = minerSubscribeResponseMessage.getSubscriptionId();
        _subscriptionMinerIds.put(subscriptionId, minerId);

        return new JsonResponse(Response.Codes.OK, minerSubscribeResponseMessage);
    }
}
