package com.abrenoch.wledgrabber.common.network.wled;

public enum ProbeError {
    INVALID_ENDPOINT,
    DNS_FAILURE,
    CONNECT_FAILURE,
    HTTP_ERROR,
    REDIRECT,
    BODY_TOO_LARGE,
    MALFORMED_JSON,
    WRONG_SHAPE,
    MISSING_FIELD,
    TIMEOUT,
    BUSY
}
