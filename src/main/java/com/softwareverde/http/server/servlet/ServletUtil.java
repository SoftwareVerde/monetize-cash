package com.softwareverde.http.server.servlet;

import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public class ServletUtil {
    public static Json createSuccessJson() {
        final Json json = new Json(false);
        json.put("wasSuccess", 1);
        json.put("errorCode", null);
        json.put("errorMessage", null);
        return json;
    }

    public static Json createSuccessJson(final Jsonable jsonable) {
        final Json json = ServletUtil.createSuccessJson();
        json.put("result", jsonable);
        return json;
    }

    public static Json createErrorJson(final Integer responseCode, final String errorMessage) {
        final Json json = new Json(false);
        json.put("wasSuccess", 0);
        json.put("errorCode", responseCode);
        json.put("errorMessage", errorMessage);
        return json;
    }

    public static JsonResponse createSuccessResponse() {
        final Json responseJson = ServletUtil.createSuccessJson();
        return new JsonResponse(Response.Codes.OK, responseJson);
    }

    public static JsonResponse createErrorResponse(final Integer responseCode, final String errorMessage) {
        final Json responseJson = ServletUtil.createErrorJson(responseCode, errorMessage);
        return new JsonResponse(responseCode, responseJson);
    }

    protected ServletUtil() { }
}
