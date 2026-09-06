package com.spendscope.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when an uploaded file is rejected as a whole (too big, unparseable, ...). */
public class ImportRejectedException extends ApiException {

    public ImportRejectedException(ErrorCode code, HttpStatus status, String message) {
        super(status, code, message);
    }

    public static ImportRejectedException tooLarge(String message) {
        return new ImportRejectedException(ErrorCode.PAYLOAD_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    public static ImportRejectedException invalid(String message) {
        return new ImportRejectedException(ErrorCode.IMPORT_FAILED, HttpStatus.BAD_REQUEST, message);
    }
}
