package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 本次出库的收货和物流信息快照。 */
@Data
public class OutboundShippingDTO {
    @NotBlank @Size(max = 300)
    private String address;
    @NotBlank @Size(max = 80)
    private String recipient;
    @NotBlank @Size(max = 40)
    private String phone;
    @Size(max = 80)
    private String sender;
    @Size(max = 40)
    private String senderPhone;
    @Size(max = 40)
    private String method;
    @Size(max = 80)
    private String carrier;
    @Size(max = 100)
    private String trackingNumber;
    @Size(max = 8000)
    private String purchaseNumber;
    @Size(max = 200)
    private String remark;
}
