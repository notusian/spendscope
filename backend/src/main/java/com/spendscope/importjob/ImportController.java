package com.spendscope.importjob;

import com.spendscope.common.web.PageResponse;
import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@Validated
@Tag(name = "Imports")
public class ImportController {

    private final CsvImportService csvImportService;

    public ImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    /**
     * @param accountId which of the caller's accounts receives the rows.
     *                  Ownership is verified before the file is parsed.
     */
    @PostMapping(path = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import transactions from a CSV file")
    public ImportSummaryResponse importCsv(@AuthenticationPrincipal AppUserDetails principal,
                                           @RequestParam @NotNull(message = "Account is required") Long accountId,
                                           @RequestPart("file") MultipartFile file) {
        return csvImportService.importCsv(principal.getUserId(), accountId, file);
    }

    @GetMapping
    @Operation(summary = "List the caller's past imports")
    public PageResponse<ImportJobResponse> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return csvImportService.listJobs(principal.getUserId(), page, size);
    }
}
