"use strict";

(async function() {
    const hexUtil = { };
    hexUtil.hexStringToByteArray = function(hexString) {
        return window.libauth.hexToBin(hexString);
    };
    hexUtil.byteArrayToHexString = function(hexString) {
        return window.libauth.binToHex(hexString);
    };

    const hashUtil = { };
    hashUtil._sha256 = await window.libauth.instantiateSha256();
    hashUtil.sha256 = function(data) {
        const sha256 = hashUtil._sha256;
        return sha256.hash(data);
    };

    const byteUtil = { };
    byteUtil.concatenateBytes = function(...byteArrays) {
        // Allow for an array to be provided without using fn.apply.
        if (byteArrays.length == 1) {
            byteArrays = byteArrays[0];
        }

        const byteArrayCount = byteArrays.length;
        let totalByteCount = 0;
        for (let i = 0; i < byteArrayCount; i += 1) {
            totalByteCount += byteArrays[i].length;
        }

        let index = 0;
        const concatenatedBytes = new Uint8Array(totalByteCount);
        for (let i = 0; i < byteArrayCount; i += 1) {
            const byteArray = byteArrays[i];
            concatenatedBytes.set(byteArray, index);
            index += byteArray.length;
        }

        return concatenatedBytes;
    };
    byteUtil.reverseEndian = function(byteArray) {
        const result = new Uint8Array(byteArray.length);
        for (let i = 0; i < byteArray.length; i += 1) {
            result[i] = byteArray[byteArray.length - i - 1];
        }
        return result;
    };
    byteUtil.increment = function(byteArray) {
        let carry = 0;
        for (let i = (byteArray.length - 1); i >= 0; i -= 1) {
            if (byteArray[i] < 255) {
                byteArray[i] += 1;
                carry = 0;
                break;
            }

            byteArray[i] = 0;
            carry = 1;
        }

        const didOverflow = (carry > 0);
        return didOverflow;
    };

    const hashTransaction = function(transactionBytes) {
        let bytes = hashUtil.sha256(transactionBytes);
        bytes = hashUtil.sha256(bytes);
        return byteUtil.reverseEndian(bytes);
    };

    const hashBlock = function(blockBytes) {
        let bytes = hashUtil.sha256(blockBytes);
        bytes = hashUtil.sha256(bytes);
        return byteUtil.reverseEndian(bytes);
    };

    const isDifficultySatisfied = function(minDifficulty, blockHash) {
        for (let i = 0; i < 32; i += 1) {
            const minByte = minDifficulty[i];
            const byte = blockHash[i];

            if (minByte > byte) { return true; }
            if (byte > minByte) { return false; }
        }
        return true;
    };

    const hashMerkleRoot = function(coinbaseTransaction, merkleTreeBranches) {
        const coinbaseTransactionHash = hashTransaction(coinbaseTransaction);
        const coinbaseTransactionHashLe = byteUtil.reverseEndian(coinbaseTransactionHash);

        let hash = coinbaseTransactionHashLe;
        for (let i = 0; i < merkleTreeBranches.length; i += 1) {
            const branchHash = merkleTreeBranches[i];
            const byteBuffer = byteUtil.concatenateBytes(hash, branchHash);

            // Double Sha256 Hash
            hash = hashUtil.sha256(byteBuffer);
            hash = hashUtil.sha256(hash);
        }

        return byteUtil.reverseEndian(hash);
    };

    const mineBlock = function(subscription, shareDifficulty, minerNotify) {
        const shareDifficultyBytes = hexUtil.hexStringToByteArray(shareDifficulty);

        const jobId = minerNotify[0];
        const previousBlockHashLe = hexUtil.hexStringToByteArray(minerNotify[1]);
        const coinbaseHead = hexUtil.hexStringToByteArray(minerNotify[2]);
        const coinbaseTail = hexUtil.hexStringToByteArray(minerNotify[3]);

        const merkleTreeBranches = [];
        const hexStringMerkleTreeBranches = minerNotify[4];
        for (let i = 0; i < hexStringMerkleTreeBranches.length; i += 1) {
            const merkleTreeBranchHexString = hexStringMerkleTreeBranches[i];
            const merkleTreeBranch = hexUtil.hexStringToByteArray(merkleTreeBranchHexString);
            merkleTreeBranches.push(merkleTreeBranch);
        }

        const blockVersion = byteUtil.reverseEndian(hexUtil.hexStringToByteArray(minerNotify[5]));
        const blockDifficulty = byteUtil.reverseEndian(hexUtil.hexStringToByteArray(minerNotify[6]));
        const blockTimestampLe = byteUtil.reverseEndian(hexUtil.hexStringToByteArray(minerNotify[7]));

        const blockNonce = new Uint8Array(4);
        const extraNonce = hexUtil.hexStringToByteArray(subscription.extraNonce);
        const extraNonce2 = new Uint8Array(subscription.extraNonce2ByteCount);

        const calculateMerkleRoot = function(extraNonce2) {
            const coinbaseTransactionBytes = byteUtil.concatenateBytes(coinbaseHead, extraNonce, extraNonce2, coinbaseTail);
            const merkleRoot = hashMerkleRoot(coinbaseTransactionBytes, merkleTreeBranches);

            return byteUtil.reverseEndian(merkleRoot);
        };

        let merkleRoot = calculateMerkleRoot(extraNonce2);

        const startTime = Date.now();
        let hashCount = 0;

        let isValid = false;
        do {
            const didOverflow = byteUtil.increment(blockNonce);
            if (didOverflow) {
                byteUtil.increment(extraNonce2);
                merkleRoot = calculateMerkleRoot(extraNonce2);
            }

            const blockHeaderBytes = byteUtil.concatenateBytes(blockVersion, previousBlockHashLe, merkleRoot, blockTimestampLe, blockDifficulty, blockNonce);
            const blockHashBytes = hashBlock(blockHeaderBytes);

            const blockHash = hashBlock(blockHeaderBytes);

            isValid = isDifficultySatisfied(shareDifficultyBytes, blockHash);

            hashCount += 1;
        } while (! isValid);

        const endTime = Date.now();
        const elapsed = (endTime - startTime);
        const hashesPerSecond = (hashCount / elapsed).toFixed(2);
        console.log(hashCount + " hashes in " + elapsed + " seconds. (" + hashesPerSecond + "h/s)");

        const blockNonceLe = byteUtil.reverseEndian(blockNonce);
        const blockTimestamp = byteUtil.reverseEndian(blockTimestampLe);

        const submitWorkParameters = [
            subscription.id,                                // workerUsername
            jobId,                                          // taskId
            hexUtil.byteArrayToHexString(extraNonce2),      // extraNonce2
            hexUtil.byteArrayToHexString(blockTimestamp),   // blockTimestamp
            hexUtil.byteArrayToHexString(blockNonceLe)      // blockNonce
        ];

        return submitWorkParameters;
    };

    const api = { };
    api.subscribe = function(callback) {
        Http.get("/api/v1/monetize/subscribe", { }, function(data) {
            const resultJson = data.result; // [subscriptions, extraNonce, extraNonce2ByteCount]
            if ( (! resultJson) || (resultJson.length < 3) ) {
                console.log("Error subscribing to stratum.");
                if (typeof callback == "function") {
                    callback(null);
                }
                return;
            }

            let subscriptionId = null;
            const subscriptionsJson = resultJson[0];
            for (let i = 0; i < subscriptionsJson.length; i += 1) {
                const subscriptionJson = subscriptionsJson[i];
                if ( (! subscriptionJson) || (subscriptionJson.length < 2) ) { continue; }

                const serviceType = subscriptionJson[0];
                const identifier = subscriptionJson[1];

                if (serviceType == "mining.notify") {
                    subscriptionId = identifier;
                    break;
                }
            }

            if (! subscriptionId) {
                console.log("Error subscribing to stratum.");
                if (typeof callback == "function") {
                    callback(null);
                }
                return;
            }

            const subscription = {
                id: subscriptionId,
                extraNonce: resultJson[1],
                extraNonce2ByteCount: resultJson[2]
            };

            if (typeof callback == "function") {
                callback(subscription);
            }
        });
    };
    api.getWork = function(subscriptionId, callback) {
        const parameters = {
            subscriptionId: subscriptionId
        };

        Http.get("/api/v1/monetize/get-work", parameters, function(data) {
            const resultJson = data.result;
            if ( (! resultJson) || (resultJson.method != "mining.notify") || (! resultJson.params)) {
                console.log("Error loading work from stratum.");
                if (typeof callback == "function") {
                    callback(null, null);
                }
                return;
            }

            const shareDifficulty = resultJson.shareDifficulty;
            const parametersJson = resultJson.params; // [jobId, previousBlockHash, coinbaseHead, coinbaseTail, [merkleTreeBranches], versionBytes, difficultyBytes, timestampBytes, shouldAbandonOldJobs]
            if (typeof callback == "function") {
                callback(shareDifficulty, parametersJson);
            }
        });
    };

    const appendMonetizationParameters = function(srcUrl, blockParameters) {
        const parameterName = "Monetization";

        let url = null;
        try {
            url = new URL(srcUrl);
        }
        catch (exception) {
            let httpUrl = window.location.origin;
            if ( (! httpUrl.endsWith("/")) && (! srcUrl.startsWith("/")) ) {
                httpUrl += "/";
            }

            url = new URL(httpUrl + srcUrl); // URL requires an absolute url...
        }

        const searchParams = url.searchParams;

        searchParams.set(parameterName, JSON.stringify(blockParameters));
        return url.toString();
    };

    const loadElement = function(element, subscription, shareDifficulty, minerNotify) {
        subscription = (subscription || window.Monetize.state.subscription);
        const blockParameters = window.Monetize.mineBlock(subscription, shareDifficulty, minerNotify);

        const monetizeSrc = element.getAttribute("monetize-src");
        const monetizeHref = element.getAttribute("monetize-href");

        if (monetizeSrc) {
            const newUrl = appendMonetizationParameters(monetizeSrc, blockParameters);
            element.setAttribute("src", newUrl);
        }
        else if (monetizeHref) {
            const newUrl = appendMonetizationParameters(monetizeHref, blockParameters);
            element.setAttribute("href", newUrl);
        }

        element.setAttribute("monetize-src", null);
        element.setAttribute("monetize-href", null);
    };

    const monetize = {
        state: { },

        HexUtil: hexUtil,
        HashUtil: hashUtil,
        Api: api,

        hashMerkleRoot: hashMerkleRoot,
        hashBlock: hashBlock,
        mineBlock: mineBlock,
        loadElement: loadElement,
        appendMonetizationParameters: appendMonetizationParameters
    };

    window.Monetize = monetize;
})();

window.setTimeout(function() {
    window.Monetize.Api.subscribe(function(subscription) {
        window.Monetize.state.subscription = subscription;

        window.Monetize.Api.getWork(subscription.id, function(shareDifficulty, minerNotify) {
            const hrefElements = document.querySelectorAll("[monetize-href]");
            const srcElements = document.querySelectorAll("[monetize-src]");

            for (let i = 0; i < hrefElements.length; i += 1) {
                const element = hrefElements[i];
                try {
                    window.Monetize.loadElement(element, subscription, shareDifficulty, minerNotify);
                }
                catch (exception) {
                    console.log(element, exception);
                }
            }

            for (let i = 0; i < srcElements.length; i += 1) {
                const element = srcElements[i];
                try {
                    window.Monetize.loadElement(element, subscription, shareDifficulty, minerNotify);
                }
                catch (exception) {
                    console.log(element, exception);
                }
            }

            window.dispatchEvent(new Event("load"));
        });
    });
}, 0);
