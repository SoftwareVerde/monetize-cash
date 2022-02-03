package com.softwareverde.http.server.servlet;

import com.softwareverde.bitcoin.server.module.stratum.BitcoinCoreStratumServer;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
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

    protected Response _getInvalidPaymentResponse() {
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

        final Headers headers = request.getHeaders();
        if (! headers.containsHeader(MonetizedServlet.HEADER_NAME)) {
            return _getInvalidPaymentResponse();
        }

        final List<String> headerValues = headers.getHeader(MonetizedServlet.HEADER_NAME);
        if (headerValues.isEmpty()) {
            return _getInvalidPaymentResponse();
        }
        final String headerValue = headerValues.get(0);
        final Json workerSubmitMessage = Json.parse(headerValue);
        Logger.debug(workerSubmitMessage);

        final Boolean isValidShare = _stratumServer.submitShare(workerSubmitMessage);
        if (! isValidShare) {
            return _getInvalidPaymentResponse();
        }

        return super.onRequest(request);
    }
}
