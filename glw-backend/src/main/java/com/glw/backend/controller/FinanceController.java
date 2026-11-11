package com.glw.backend.controller;

import com.glw.backend.common.ApiResponse;
import com.glw.backend.finance.FinanceModels.StatementTemplate;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Statement;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.finance.FinanceModels.Allocation;
import com.glw.backend.finance.FinanceModels.Partner;
import com.glw.backend.finance.FinanceModels.Generate;
import com.glw.backend.finance.FinanceModels.PaymentInput;
import com.glw.backend.finance.FinanceModels.Allocate;
import com.glw.backend.finance.FinanceModels.SettleOrderInput;
import com.glw.backend.finance.FinanceModels.SettleOrderResult;
import com.glw.backend.finance.FinanceModels.Summary;
import com.glw.backend.finance.FinanceModels.StatementDetail;
import com.glw.backend.finance.FinanceModels.PaymentDetail;
import com.glw.backend.finance.statement.StatementFile;
import com.glw.backend.finance.statement.StatementLayoutDetail;
import com.glw.backend.finance.statement.StatementTemplateLayoutInput;
import com.glw.backend.service.FinanceOpenItemService;
import com.glw.backend.service.FinanceStatementExportService;
import com.glw.backend.service.FinanceStatementService;
import com.glw.backend.service.FinanceStatementTemplateService;
import com.glw.backend.service.FinancePaymentService;
import com.glw.backend.service.FinanceAllocationService;
import com.glw.backend.service.FinanceProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/fin")
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceOpenItemService financeOpenItemService;
    private final FinanceStatementService financeStatementService;
    private final FinanceStatementExportService financeStatementExportService;
    private final FinanceStatementTemplateService financeStatementTemplateService;
    private final FinancePaymentService financePaymentService;
    private final FinanceAllocationService financeAllocationService;
    private final FinanceProfileService financeProfileService;
    @PostMapping("/open-item/list") public ApiResponse<Page<OpenItem>> openItems(@Valid @RequestBody Query q) { return ApiResponse.ok(financeOpenItemService.openItems(q)); }
    @PostMapping("/open-item/summary") public ApiResponse<Summary> summary(@Valid @RequestBody Query q) { return ApiResponse.ok(financeOpenItemService.summary(q)); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/open-item/rebuild") public ApiResponse<Integer> rebuild() { return ApiResponse.ok(financeOpenItemService.rebuild()); }
    @PostMapping("/statement/preview") public ApiResponse<List<OpenItem>> preview(@Valid @RequestBody Query q) { return ApiResponse.ok(financeOpenItemService.preview(q)); }
    @PostMapping("/statement/generate") public ApiResponse<List<Statement>> generate(@Valid @RequestBody Generate input) { return ApiResponse.ok(financeStatementService.generate(input)); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/statement/auto-generate") public ApiResponse<Integer> autoGenerate() { return ApiResponse.ok(financeStatementService.autoGenerate()); }
    @PostMapping("/statement/list") public ApiResponse<Page<Statement>> statements(@Valid @RequestBody Query q) { return ApiResponse.ok(financeStatementService.statements(q)); }
    @GetMapping("/statement/{id}") public ApiResponse<StatementDetail> statement(@PathVariable Long id) { return ApiResponse.ok(financeStatementService.statement(id)); }
    @PostMapping("/statement/{id}/{action:send|confirm|void}") public ApiResponse<StatementDetail> transition(@PathVariable Long id, @PathVariable String action) { return ApiResponse.ok(financeStatementService.transition(id, action)); }
    @GetMapping("/statement/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(required = false) String format) {
        StatementFile file = financeStatementExportService.export(id, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
    @GetMapping("/statement/template/doc-types")
    public ApiResponse<List<com.glw.backend.finance.statement.DocumentType.Option>> templateDocTypes() {
        return ApiResponse.ok(financeStatementTemplateService.documentTypes());
    }
    @GetMapping("/statement/template/list")
    public ApiResponse<List<StatementTemplate>> templates(
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Long partnerId) {
        return ApiResponse.ok(financeStatementTemplateService.templates(docType, direction, partnerId));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/statement/template/layout")
    public ApiResponse<StatementTemplate> saveLayout(@Valid @RequestBody StatementTemplateLayoutInput input) {
        return ApiResponse.ok(financeStatementTemplateService.saveLayout(input));
    }
    @GetMapping("/statement/template/{id}/layout")
    public ApiResponse<StatementLayoutDetail> layout(@PathVariable Long id) {
        return ApiResponse.ok(financeStatementTemplateService.layout(id));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/statement/template/layout/preview")
    public ResponseEntity<byte[]> previewLayout(@RequestBody JsonNode layout,
                                                @RequestParam(defaultValue = "XLSX") String format,
                                                @RequestParam(required = false) String docType,
                                                @RequestParam(required = false) String direction) {
        StatementFile file = financeStatementTemplateService.previewLayout(layout, format, docType, direction);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/statement/template/{id}/default")
    public ApiResponse<StatementTemplate> defaultTemplate(@PathVariable Long id) {
        return ApiResponse.ok(financeStatementTemplateService.setDefault(id));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/statement/template/{id}/delete")
    public ApiResponse<StatementTemplate> deleteTemplate(@PathVariable Long id) {
        return ApiResponse.ok(financeStatementTemplateService.delete(id));
    }
    @PostMapping("/payment/create") public ApiResponse<PaymentDetail> payment(@Valid @RequestBody PaymentInput input) { return ApiResponse.ok(financePaymentService.createPayment(input)); }
    @PostMapping("/payment/settle-order") public ApiResponse<SettleOrderResult> settleOrder(@Valid @RequestBody SettleOrderInput input) { return ApiResponse.ok(financeAllocationService.settleOrder(input)); }
    @PostMapping("/payment/list") public ApiResponse<Page<Payment>> payments(@Valid @RequestBody Query q) { return ApiResponse.ok(financePaymentService.payments(q)); }
    @GetMapping("/payment/{id}") public ApiResponse<PaymentDetail> payment(@PathVariable Long id) { return ApiResponse.ok(financePaymentService.payment(id)); }
    @PostMapping("/payment/{id}/void") public ApiResponse<Void> voidPayment(@PathVariable Long id) { financePaymentService.voidPayment(id); return ApiResponse.ok(null); }
    @PostMapping("/clearing/allocate") public ApiResponse<Allocation> allocate(@Valid @RequestBody Allocate input) { return ApiResponse.ok(financeAllocationService.allocate(input)); }
    @PostMapping("/clearing/{id}/reverse") public ApiResponse<Void> reverse(@PathVariable Long id) { financeAllocationService.reverse(id); return ApiResponse.ok(null); }
    @GetMapping("/partners") public ApiResponse<List<Partner>> partners(@RequestParam String direction) { return ApiResponse.ok(financeProfileService.partners(direction)); }
    @GetMapping("/profile/list") public ApiResponse<List<com.glw.backend.finance.FinanceModels.Profile>> profiles(@RequestParam String direction) { return ApiResponse.ok(financeProfileService.profiles(direction)); }
    @PostMapping("/profile/save") public ApiResponse<com.glw.backend.finance.FinanceModels.SaveProfileResult> saveProfile(@Valid @RequestBody com.glw.backend.finance.FinanceModels.Profile input) { return ApiResponse.ok(financeProfileService.saveProfile(input)); }
}
