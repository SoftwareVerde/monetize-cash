package com.softwareverde.http.server.servlet;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.server.module.stratum.BitcoinCoreStratumServer;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerNotifyMessage;
import com.softwareverde.bitcoin.server.stratum.task.StratumUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;

import java.util.concurrent.ConcurrentHashMap;

public class GetWorkApiServlet implements Servlet {
    protected final BitcoinCoreStratumServer _stratumServer;
    protected final ConcurrentHashMap<ByteArray, Long> _subscriptionMinerIds;

    protected Difficulty _shareDifficulty;

    public GetWorkApiServlet(final BitcoinCoreStratumServer stratumServer, final ConcurrentHashMap<ByteArray, Long> subscriptionMinerIds) {
        _stratumServer = stratumServer;
        _subscriptionMinerIds = subscriptionMinerIds;

        final long multiplier = (long) Math.pow(2, 18);
        _shareDifficulty = Difficulty.BASE_DIFFICULTY.multiplyBy(multiplier);
        _stratumServer.invertDifficulty(true);
        _stratumServer.setShareDifficulty(multiplier);
    }

    @Override
    public Response onRequest(final Request request) {
        if (request.getMethod() != HttpMethod.GET) {
            final Json json = new Json(false);
            json.put("wasSuccess", false);
            json.put("errorCode", Response.Codes.BAD_REQUEST);
            json.put("errorMessage", "Bad request.");

            return new JsonResponse(Response.Codes.BAD_REQUEST, json);
        }

        final GetParameters getParameters = request.getGetParameters();
        final ByteArray subscriptionId = ByteArray.fromHexString(getParameters.get("subscriptionId"));
        final Long minerId = _subscriptionMinerIds.get(subscriptionId);

        if (minerId == null) {
            return ServletUtil.createErrorResponse(Response.Codes.BAD_REQUEST, "Invalid subscription ID.");
        }

        final MinerNotifyMessage minerNotifyMessage = _stratumServer.getMinerWork(minerId, true);
        if (minerNotifyMessage == null) {
            return ServletUtil.createErrorResponse(Response.Codes.SERVER_ERROR, "Unable to generate work.");
        }

        { // Un-swab the previousBlockHash...
            final Sha256Hash unSwabbedBlockHash = minerNotifyMessage.getLittleEndianPreviousBlockHash();
            final ByteArray swabbedBytes = StratumUtil.swabBytes(unSwabbedBlockHash);
            final Sha256Hash swabbedBlockHash = Sha256Hash.wrap(swabbedBytes.getBytes());
            minerNotifyMessage.setLittleEndianPreviousBlockHash(swabbedBlockHash);
        }

        final Json minerNotifyMessageJson = minerNotifyMessage.toJson();
        minerNotifyMessageJson.put("shareDifficulty", _shareDifficulty.getBytes());

        final Json json = ServletUtil.createSuccessJson(minerNotifyMessageJson);
        return new JsonResponse(Response.Codes.OK, json);
    }
}
