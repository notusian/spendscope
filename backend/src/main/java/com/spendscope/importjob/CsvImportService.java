package com.spendscope.importjob;

import com.spendscope.account.Account;
import com.spendscope.account.AccountService;
import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.categorization.CategorizationService;
import com.spendscope.categorization.CategorizationSession;
import com.spendscope.common.error.ImportRejectedException;
import com.spendscope.common.web.PageResponse;
import com.spendscope.transaction.Transaction;
import com.spendscope.transaction.TransactionFingerprint;
import com.spendscope.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The CSV import pipeline.
 *
 * <pre>
 * upload -> file validation -> parse -> row validation -> normalise
 *        -> merchant detection -> categorisation -> duplicate detection
 *        -> insert -> summary
 * </pre>
 *
 * <p>The whole import runs in one transaction. Either the accepted rows and the
 * job record are both written, or neither is - a half-imported statement with
 * no record of what happened is the worst outcome for a finance app.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    /** Fingerprints are looked up in chunks so the IN list stays a sane size. */
    private static final int FINGERPRINT_LOOKUP_CHUNK = 500;

    private static final int MAX_FILENAME_LENGTH = 255;

    private final CsvTransactionParser parser;
    private final TransactionRepository transactionRepository;
    private final ImportJobRepository importJobRepository;
    private final AccountService accountService;
    private final CategorizationService categorizationService;
    private final AuditService auditService;
    private final CsvImportProperties properties;

    public CsvImportService(CsvTransactionParser parser,
                            TransactionRepository transactionRepository,
                            ImportJobRepository importJobRepository,
                            AccountService accountService,
                            CategorizationService categorizationService,
                            AuditService auditService,
                            CsvImportProperties properties) {
        this.parser = parser;
        this.transactionRepository = transactionRepository;
        this.importJobRepository = importJobRepository;
        this.accountService = accountService;
        this.categorizationService = categorizationService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional
    public ImportSummaryResponse importCsv(Long userId, Long accountId, MultipartFile file) {
        long startedAt = System.nanoTime();

        // Ownership first: no work is done on a file destined for someone
        // else's account.
        Account account = accountService.require(userId, accountId);
        byte[] content = readBytes(file);
        String filename = sanitiseFilename(file.getOriginalFilename());

        ImportJob job = importJobRepository.save(new ImportJob(userId, account.getId(), filename));

        CsvTransactionParser.ParseResult parsed;
        try {
            parsed = parser.parse(content);
        } catch (ImportRejectedException ex) {
            job.fail();
            auditService.record(userId, AuditEventType.CSV_IMPORT_REJECTED,
                    Map.of("accountId", accountId, "reason", ex.getMessage()));
            throw ex;
        }

        ImportOutcome outcome = persistRows(userId, account, parsed.rows());

        int rowsRejected = parsed.rowsProcessed() - outcome.imported() - outcome.duplicates();
        job.complete(parsed.rowsProcessed(), outcome.imported(), rowsRejected, outcome.duplicates());

        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("CSV import complete userId={} accountId={} rowsProcessed={} imported={} rejected={} "
                        + "duplicates={} durationMs={}",
                userId, accountId, parsed.rowsProcessed(), outcome.imported(), rowsRejected,
                outcome.duplicates(), durationMs);

        auditService.record(userId, AuditEventType.CSV_IMPORTED, Map.of(
                "accountId", accountId,
                "importJobId", job.getId(),
                "rowsImported", outcome.imported(),
                "duplicatesDetected", outcome.duplicates()));

        return new ImportSummaryResponse(
                job.getId(),
                parsed.rowsProcessed(),
                outcome.imported(),
                rowsRejected,
                outcome.duplicates(),
                parsed.errors(),
                parsed.errorsTruncated());
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobResponse> listJobs(Long userId, int page, int size) {
        return PageResponse.from(
                importJobRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)),
                ImportJobResponse::from);
    }

    /**
     * Categorises, de-duplicates and inserts.
     *
     * <p>Duplicate detection happens against two sets: what is already in the
     * database, and what has already been accepted from <em>this</em> file.
     * Without the second, uploading a file that contains the same row twice
     * would insert it twice.
     */
    private ImportOutcome persistRows(Long userId, Account account, List<CsvRow> rows) {
        if (rows.isEmpty()) {
            return new ImportOutcome(0, 0);
        }

        CategorizationSession session = categorizationService.openSession(userId);

        // Compute every fingerprint first so existence can be checked in bulk
        // instead of one SELECT per row.
        List<PreparedRow> prepared = new ArrayList<>(rows.size());
        for (CsvRow row : rows) {
            CategorizationSession.Categorisation categorisation = session.categorise(row.description());
            String fingerprint = TransactionFingerprint.compute(
                    userId, account.getId(), row.date(), categorisation.normalisedDescription(),
                    row.amount(), account.getCurrency());
            prepared.add(new PreparedRow(row, categorisation, fingerprint));
        }

        Set<String> existingFingerprints = findExistingFingerprints(userId,
                prepared.stream().map(PreparedRow::fingerprint).collect(java.util.stream.Collectors.toSet()));

        Set<String> seenInThisFile = new HashSet<>();
        List<Transaction> toInsert = new ArrayList<>();
        int duplicates = 0;

        for (PreparedRow row : prepared) {
            boolean alreadyExists = existingFingerprints.contains(row.fingerprint());
            boolean repeatedInFile = !seenInThisFile.add(row.fingerprint());
            if (alreadyExists || repeatedInFile) {
                // Reported, never deleted or overwritten. The existing row is
                // the user's data and this import has no authority over it.
                duplicates++;
                continue;
            }
            toInsert.add(new Transaction(
                    userId,
                    account.getId(),
                    row.categorisation().categoryId(),
                    row.csvRow().date(),
                    row.csvRow().description(),
                    row.categorisation().normalisedDescription(),
                    row.categorisation().merchant(),
                    row.csvRow().amount(),
                    account.getCurrency(),
                    row.fingerprint()));
        }

        transactionRepository.saveAll(toInsert);
        return new ImportOutcome(toInsert.size(), duplicates);
    }

    private Set<String> findExistingFingerprints(Long userId, Set<String> fingerprints) {
        Set<String> existing = new HashSet<>();
        List<String> asList = new ArrayList<>(fingerprints);
        for (int start = 0; start < asList.size(); start += FINGERPRINT_LOOKUP_CHUNK) {
            List<String> chunk = asList.subList(start, Math.min(start + FINGERPRINT_LOOKUP_CHUNK, asList.size()));
            transactionRepository.findByUserIdAndFingerprintIn(userId, chunk)
                    .forEach(transaction -> existing.add(transaction.getFingerprint()));
        }
        return existing;
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ImportRejectedException.invalid("No file was uploaded");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw ImportRejectedException.tooLarge(
                    "The file is larger than " + properties.getMaxFileSize().toMegabytes() + " MB");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            log.warn("Could not read uploaded file", ex);
            throw ImportRejectedException.invalid("The uploaded file could not be read");
        }
    }

    /**
     * Makes a client-supplied filename safe to store and display.
     *
     * <p>The filename is only ever a label - nothing opens a file by this name -
     * but it is attacker-controlled text that gets rendered in the UI and
     * written to the database, so directory components and control characters
     * come off before it goes anywhere.
     */
    static String sanitiseFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.csv";
        }
        // Strip any path, whichever separator style the client used.
        String name = originalFilename;
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        // "..", "." and the empty string are all useless as labels and are the
        // classic traversal payloads.
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "upload.csv";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
    }

    private record PreparedRow(CsvRow csvRow,
                               CategorizationSession.Categorisation categorisation,
                               String fingerprint) {
    }

    private record ImportOutcome(int imported, int duplicates) {
    }
}
