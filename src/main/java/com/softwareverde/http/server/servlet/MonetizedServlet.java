package com.softwareverde.http.server.servlet;

import com.softwareverde.bitcoin.server.module.stratum.BitcoinCoreStratumServer;
import com.softwareverde.concurrent.ConcurrentHashSet;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.util.HashSet;
import java.util.List;

public class MonetizedServlet extends DirectoryServlet {
    public static final String HEADER_NAME = "Monetization";

    protected static class EndpointMatcher {
        public final String endpoint;
        public final Boolean strictMatchIsEnabled;

        public EndpointMatcher(final String endpoint, final Boolean strictMatchIsEnabled) {
            this.endpoint = endpoint;
            this.strictMatchIsEnabled = strictMatchIsEnabled;
        }

        public Boolean matches(final String endpoint) {
            if (this.strictMatchIsEnabled) {
                return Util.areEqual(endpoint, this.endpoint);
            }

            if ( (endpoint == null) || (this.endpoint == null) ) { return false; }

            final String lowerCaseEndpoint = endpoint.toLowerCase();
            final String thisLowerCaseEndpoint = this.endpoint.toLowerCase();
            return lowerCaseEndpoint.startsWith(thisLowerCaseEndpoint);
        }

        @Override
        public boolean equals(final Object object) {
            if (! (object instanceof EndpointMatcher)) { return false; }

            final EndpointMatcher endpointMatcher = (EndpointMatcher) object;
            return Util.areEqual(endpointMatcher.endpoint, this.endpoint);
        }

        @Override
        public int hashCode() {
            if (this.endpoint == null) { return 0; }
            return this.endpoint.hashCode();
        }
    }

    protected final Integer _maxSubmittedShareHistory = 1048576;
    protected final ConcurrentHashSet<Object> _submittedShares = new ConcurrentHashSet<>();
    protected final BitcoinCoreStratumServer _stratumServer;
    protected final HashSet<EndpointMatcher> _freeEndpoints = new HashSet<>();

    protected Boolean _isFreeRequest(final Request request) {
        final String filePath = request.getFilePath();
        for (final EndpointMatcher endpointMatcher : _freeEndpoints) {
            if (endpointMatcher.matches(filePath)) {
                return true;
            }
        }
        return false;
    }

    protected Response _createInvalidPaymentResponse() {
        final Integer paymentRequiredCode = 402;

        final Json jsonResponse = new Json(false);
        jsonResponse.put("wasSuccess", 0);
        jsonResponse.put("errorMessage", "Payment required.");

        final Response response = new Response();
        response.setCode(paymentRequiredCode);
        response.setContent(jsonResponse.toString());

        response.setHeader(Response.Headers.CONTENT_TYPE, "application/json");

        return response;
    }

    protected Json _getWorkerSubmitMessage(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        if (getParameters.containsKey(MonetizedServlet.HEADER_NAME)) {
            final String stringValue = getParameters.get(MonetizedServlet.HEADER_NAME);
            final Json workerSubmitMessage = Json.parse(stringValue);
            if (workerSubmitMessage.isArray() && workerSubmitMessage.length() > 0) {
                return workerSubmitMessage;
            }
        }

        final PostParameters postParameters = request.getPostParameters();
        if (postParameters.containsKey(MonetizedServlet.HEADER_NAME)) {
            final String stringValue = postParameters.get(MonetizedServlet.HEADER_NAME);
            final Json workerSubmitMessage = Json.parse(stringValue);
            if (workerSubmitMessage.isArray() && workerSubmitMessage.length() > 0) {
                return workerSubmitMessage;
            }
        }

        final Headers headers = request.getHeaders();
        if (headers.containsHeader(MonetizedServlet.HEADER_NAME)) {
            final List<String> headerValues = headers.getHeader(MonetizedServlet.HEADER_NAME);
            if (! headerValues.isEmpty()) {
                final String headerValue = headerValues.get(0);
                final Json workerSubmitMessage = Json.parse(headerValue);
                if (workerSubmitMessage.isArray() && workerSubmitMessage.length() > 0) {
                    return workerSubmitMessage;
                }
            }
        }

        return null;
    }

    protected Sha256Hash _calculateShareIdentifier(final Json workerSubmitMessage) {
        final String workerUsername = workerSubmitMessage.getString(0); // Unused.
        final String taskIdHex = workerSubmitMessage.getString(1);
        final String stratumExtraNonce2 = workerSubmitMessage.getString(2);
        final String stratumTimestamp = workerSubmitMessage.getString(3);
        final String stratumNonce = workerSubmitMessage.getString(4);

        // Put the parameters into their canonical form to prevent reusing malleable shares...
        final Long taskId = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(taskIdHex));
        final ByteArray extraNonce2 = ByteArray.wrap(HexUtil.hexStringToByteArray(stratumExtraNonce2));
        final Long nonce = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumNonce));
        final Long timestamp = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumTimestamp));

        final String canonicalMessage;
        {
            final Json canonicalMessageJson = new Json(true);
            canonicalMessageJson.add(taskId);
            canonicalMessageJson.add(extraNonce2);
            canonicalMessageJson.add(nonce);
            canonicalMessageJson.add(timestamp);
            canonicalMessage = canonicalMessageJson.toString();
        }

        final ByteArray canonicalMessageBytes = ByteArray.wrap(StringUtil.stringToBytes(canonicalMessage));
        return HashUtil.sha256(canonicalMessageBytes);
    }

    public MonetizedServlet(final File directory, final BitcoinCoreStratumServer stratumServer) {
        super(directory);
        _stratumServer = stratumServer;
    }

    public void addFreeEndpoint(final String endpoint, final Boolean strictMatchEnabled) {
        _freeEndpoints.add(new EndpointMatcher(endpoint, strictMatchEnabled));
    }

    @Override
    public Response onRequest(final Request request) {
        if (_isFreeRequest(request)) {
            return super.onRequest(request);
        }

        final Json workerSubmitMessage = _getWorkerSubmitMessage(request);
        if (workerSubmitMessage == null) {
            Logger.debug("Payment required: " + request.getFilePath());
            return _createInvalidPaymentResponse();
        }

        final Boolean isValidShare = _stratumServer.submitShare(workerSubmitMessage);
        if (! isValidShare) {
            Logger.debug("Payment required: " + request.getFilePath());
            return _createInvalidPaymentResponse();
        }

        final Sha256Hash shareIdentifier = _calculateShareIdentifier(workerSubmitMessage);
        final boolean isUnique = _submittedShares.add(shareIdentifier);
        if (! isUnique) {
            Logger.debug("Payment required: " + request.getFilePath());
            return _createInvalidPaymentResponse();
        }

        if (_submittedShares.size() >= _maxSubmittedShareHistory) {
            _submittedShares.clear();
        }

        return super.onRequest(request);
    }
}
