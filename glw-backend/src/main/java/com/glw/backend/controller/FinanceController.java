package com.glw.backend.controller;

import com.glw.backend.common.ApiResponse;
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
import com.glw.backend.finance.FinanceModels.TaxInput;
import com.glw.backend.finance.FinanceModels.Summary;
import com.glw.backend.finance.FinanceModels.StatementDetail;
import com.glw.backend.finance.FinanceModels.PaymentDetail;
import com.glw.backend.service.FinanceOpenItemService;
import com.glw.backend.service.FinanceStatementService;
import com.glw.backend.service.FinancePaymentService;
import com.glw.backend.service.FinanceAllocationService;
import com.glw.backend.service.FinanceProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/fin")
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceOpenItemService financeOpenItemService;
    private final FinanceStatementService financeStatementService;
    private final FinancePaymentService financePaymentService;
    private final FinanceAllocationService financeAllocationService;
    private final FinanceProfileService financeProfileService;
    @PostMapping("/open-item/list") public ApiResponse<Page<OpenItem>> openItems(@Valid @RequestBody Query q) { return ApiResponse.ok(financeOpenItemService.openItems(q)); }
    @PostMapping("/open-item/summary") public ApiResponse<Summary> summary(@Valid @RequestBody Query q) { return ApiResponse.ok(financeOpenItemService.summary(q)); }
    @PostMapping("/open-item/{id}/tax-rate") public ApiResponse<OpenItem> taxRate(@PathVariable Long id, @Valid @RequestBody TaxInput input) { return ApiResponse.ok(financeOpenItemService.resolveTax(id, input)); }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/open-item/rebuild") public ApiResponse<Integer> rebuild() { return ApiResponse.ok(financeOpenItemService.rebuild()); }
    @PostMapping("/statement/preview") public ApiResponse<List<OpenItem>> preview(@Valid @RequestBody Query q) { return ApiResponse.ok(financeOpenItemService.preview(q)); }
    @PostMapping("/statement/generate") public ApiResponse<StatementDetail> generate(@Valid @RequestBody Generate input) { return ApiResponse.ok(financeStatementService.generate(input)); }
    @PostMapping("/statement/list") public ApiResponse<Page<Statement>> statements(@Valid @RequestBody Query q) { return ApiResponse.ok(financeStatementService.statements(q)); }
    @GetMapping("/statement/{id}") public ApiResponse<StatementDetail> statement(@PathVariable Long id) { return ApiResponse.ok(financeStatementService.statement(id)); }
    @PostMapping("/statement/{id}/{action:send|confirm|void}") public ApiResponse<StatementDetail> transition(@PathVariable Long id, @PathVariable String action) { return ApiResponse.ok(financeStatementService.transition(id, action)); }
    @GetMapping("/statement/{id}/export") public ResponseEntity<byte[]> export(@PathVariable Long id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement-" + id + ".csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(financeStatementService.exportStatement(id));
    }
    @PostMapping("/payment/create") public ApiResponse<PaymentDetail> payment(@Valid @RequestBody PaymentInput input) { return ApiResponse.ok(financePaymentService.createPayment(input)); }
    @PostMapping("/payment/list") public ApiResponse<Page<Payment>> payments(@Valid @RequestBody Query q) { return ApiResponse.ok(financePaymentService.payments(q)); }
    @GetMapping("/payment/{id}") public ApiResponse<PaymentDetail> payment(@PathVariable Long id) { return ApiResponse.ok(financePaymentService.payment(id)); }
    @PostMapping("/payment/{id}/void") public ApiResponse<Void> voidPayment(@PathVariable Long id) { financePaymentService.voidPayment(id); return ApiResponse.ok(null); }
    @PostMapping("/clearing/allocate") public ApiResponse<Allocation> allocate(@Valid @RequestBody Allocate input) { return ApiResponse.ok(financeAllocationService.allocate(input)); }
    @PostMapping("/clearing/{id}/reverse") public ApiResponse<Void> reverse(@PathVariable Long id) { financeAllocationService.reverse(id); return ApiResponse.ok(null); }
    @GetMapping("/partners") public ApiResponse<List<Partner>> partners(@RequestParam String direction) { return ApiResponse.ok(financeProfileService.partners(direction)); }
    @GetMapping("/profile/list") public ApiResponse<List<com.glw.backend.finance.FinanceModels.Profile>> profiles(@RequestParam String direction) { return ApiResponse.ok(financeProfileService.profiles(direction)); }
    @PostMapping("/profile/save") public ApiResponse<Void> saveProfile(@Valid @RequestBody com.glw.backend.finance.FinanceModels.Profile input) { financeProfileService.saveProfile(input); return ApiResponse.ok(null); }
}
