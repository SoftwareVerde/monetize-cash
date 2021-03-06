"use strict";
class Http {
    static _jsonToQueryString(jsonData) {
        return Object.keys((jsonData ? jsonData : { })).map(key => window.encodeURIComponent(key) + "=" + window.encodeURIComponent(jsonData[key])).join("&");
    }

    static _getPostParameters(parameters) {
        const postParameters = {
            url: parameters[0],
            headers: { },
            getData: null,
            postData: null,
            callbackFunction: null
        };

        if (parameters.length >= 4) {                    // url, getData, postData, callbackFunction, (headers)
            postParameters.getData = parameters[1];
            postParameters.postData = parameters[2];
            postParameters.callbackFunction = parameters[3];
            if (parameters.length == 5) {
                postParameters.headers = parameters[4]
            }
        }
        else if (parameters.length == 2) {               // url, postData
            postParameters.postData = parameters[1];
        }
        else {
            if (typeof parameters[2] == "function") {    // url, postData, callbackFunction
                postParameters.postData = parameters[1];
                postParameters.callbackFunction = parameters[2];
            }
            else {                                      // url, getData, postData
                postParameters.getData = parameters[1];
                postParameters.postData = parameters[2];
            }
        }

        return postParameters;
    }

    static _send(method, url, headers, getQueryString, rawPostData, callbackFunction, parseJsonResponse) {
        const request = new Request(
            url + (getQueryString? ("?" + getQueryString) : ""),
            {
                method:         method,
                credentials:    "include",
                headers: headers,
                body:           (rawPostData ? rawPostData : null)
            }
        );

        window.fetch(request, { credentials: "same-origin" })
            .then(function(response) {
                if (! parseJsonResponse) {
                    return response.blob();
                }

                const contentType = (response.headers.get("content-type") || "");
                if (contentType.includes("application/json")) {
                    return response.json();
                }
                return { wasSuccess: false, errorMessage: "Invalid response." };
            })
            .then(function(json) {
                if (typeof callbackFunction == "function") {
                    window.setTimeout(function() {
                        callbackFunction(json);
                    }, 0);
                }
            })
            .catch(function(error) {
                if (typeof callbackFunction == "function") {
                    const json = { wasSuccess: false, errorMessage: "No response." };
                    callbackFunction(json);
                }
            });
    }

    static get(url, getData, callbackFunction, headers) {
        headers = (headers || { });
        Http._send("GET", url, headers, Http._jsonToQueryString(getData), null, callbackFunction, true);
    }

    static getRaw(url, getData, callbackFunction, headers) {
        headers = (headers || { });
        Http._send("GET", url, headers, Http._jsonToQueryString(getData), null, callbackFunction, false);
    }

    static post() { // Params: url, getData, postData, callbackFunction
        const postParameters = Http._getPostParameters(arguments);
        Http._send("POST", postParameters.url, { }, Http._jsonToQueryString(postParameters.getData), Http._jsonToQueryString(postParameters.postData), postParameters.callbackFunction, true);
    }

    static postJson() { // Params: url, getData, postData, callbackFunction
        const postParameters = Http._getPostParameters(arguments);
        Http._send("POST", postParameters.url, { }, Http._jsonToQueryString(postParameters.getData), postParameters.postData, postParameters.callbackFunction, true);
    }
}
