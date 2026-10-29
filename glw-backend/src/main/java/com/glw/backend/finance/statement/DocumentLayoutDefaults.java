package com.glw.backend.finance.statement;

/**
 * 每类单据的系统全局默认模板。
 *
 * 默认模板本身也是普通可视化布局：用户可以直接编辑、另存为往来单位专属模板，
 * 系统只负责在某个类型没有任何模板时补一份，保证模板中心始终有可用底稿。
 */
public final class DocumentLayoutDefaults {
    private DocumentLayoutDefaults() {
    }

    public static String layout(DocumentType type, String direction) {
        String json = switch (type) {
            case STATEMENT -> STATEMENT;
            case SALES_CONTRACT, PURCHASE_CONTRACT -> CONTRACT;
            case DELIVERY_NOTE -> DELIVERY_NOTE;
        };
        String partnerLabel = "AP".equals(direction) ? "供应商：" : "客户：";
        return json.replace("__PARTNER_LABEL__", partnerLabel);
    }

    private static final String STATEMENT = """
            {
              "page": {"size":"A4","orientation":"PORTRAIT","marginMm":10,"gridMm":3},
              "components": [
                {"id":"title","type":"text","x":15,"y":10,"w":180,"h":12,
                 "text":"{{header.title}}","fontSize":20,"bold":true,"align":"center"},
                {"id":"company","type":"field","x":15,"y":26,"w":105,"h":7,
                 "field":"company.name","fontSize":10,"bold":true,"align":"left"},
                {"id":"period","type":"field","x":120,"y":26,"w":75,"h":7,
                 "label":"对账期间：","field":"header.periodLabel","fontSize":10,"align":"right"},
                {"id":"partner","type":"field","x":15,"y":35,"w":105,"h":7,
                 "label":"__PARTNER_LABEL__","field":"partner.name","fontSize":10,"align":"left"},
                {"id":"date","type":"field","x":120,"y":35,"w":75,"h":7,
                 "label":"对账日期：","field":"header.statementDateLabel","fontSize":10,"align":"right"},
                {"id":"items","type":"table","x":15,"y":47,"w":180,"h":36,
                 "headerHeight":8,"rowHeight":7,"fontSize":9,"headerBold":true,"border":true,
                 "columns":[
                   {"title":"序号","field":"item.seq","width":12,"align":"center"},
                   {"title":"送货日期","field":"item.bizDateText","width":25,"align":"center"},
                   {"title":"送货单号","field":"item.sourceNo","width":35},
                   {"title":"客户订单号","field":"item.partnerOrderNo","width":33},
                   {"title":"型号","field":"item.partNo","width":30},
                   {"title":"数量","field":"item.quantity","width":15,"align":"right"},
                   {"title":"单价","field":"item.unitPrice","width":15,"align":"right"},
                   {"title":"金额","field":"item.amountGross","width":15,"align":"right"}
                 ]},
                {"id":"total-label","type":"text","x":15,"y":90,"w":80,"h":7,
                 "text":"合计（大写）：","fontSize":10,"bold":true,"align":"left"},
                {"id":"total","type":"field","x":100,"y":90,"w":95,"h":7,
                 "label":"合计金额：","field":"totals.totalGross","fontSize":10,"bold":true,"align":"right"},
                {"id":"note","type":"text","x":15,"y":102,"w":180,"h":7,
                 "text":"说明：以上明细请核对，如有异议请于收到后 3 个工作日内提出。","fontSize":10,"align":"left"},
                {"id":"sign","type":"signature","x":15,"y":250,"w":180,"h":30,
                 "text":"客户确认（盖章）：                              供应商确认（盖章）：",
                 "fontSize":10,"align":"left","border":true}
              ]
            }
            """;

    private static final String CONTRACT = """
            {
              "page": {"size":"A4","orientation":"PORTRAIT","marginMm":10,"gridMm":3},
              "components": [
                {"id":"title","type":"text","x":15,"y":10,"w":180,"h":14,
                 "text":"{{header.title}}","fontSize":20,"bold":true,"align":"center"},
                {"id":"company","type":"field","x":15,"y":26,"w":180,"h":7,
                 "field":"company.name","fontSize":10,"bold":true,"align":"left"},
                {"id":"partner","type":"field","x":15,"y":34,"w":110,"h":7,
                 "label":"往来单位：","field":"partner.name","fontSize":10,"align":"left"},
                {"id":"no","type":"field","x":125,"y":34,"w":70,"h":7,
                 "label":"合同编号：","field":"header.no","fontSize":10,"align":"right"},
                {"id":"contact","type":"field","x":15,"y":42,"w":110,"h":7,
                 "label":"联系人：","field":"partner.contact","fontSize":10,"align":"left"},
                {"id":"date","type":"field","x":125,"y":42,"w":70,"h":7,
                 "label":"签订日期：","field":"header.dateLabel","fontSize":10,"align":"right"},
                {"id":"items","type":"table","x":15,"y":52,"w":180,"h":54,
                 "headerHeight":8,"rowHeight":7,"fontSize":9,"headerBold":true,"border":true,
                 "columns":[
                   {"title":"序号","field":"item.seq","width":12,"align":"center"},
                   {"title":"产品名称","field":"item.productName","width":40},
                   {"title":"规格型号","field":"item.spec","width":35},
                   {"title":"封装","field":"item.packageType","width":20},
                   {"title":"数量","field":"item.quantity","width":15,"align":"right"},
                   {"title":"单位","field":"item.unit","width":12,"align":"center"},
                   {"title":"单价","field":"item.unitPrice","width":18,"align":"right"},
                   {"title":"金额","field":"item.amountGross","width":28,"align":"right"}
                 ]},
                {"id":"quantity","type":"field","x":15,"y":112,"w":60,"h":7,
                 "label":"合计数量：","field":"totals.totalQuantity","fontSize":10,"align":"left"},
                {"id":"amount","type":"field","x":105,"y":112,"w":90,"h":7,
                 "label":"合同总额：","field":"totals.totalAmount","fontSize":10,"bold":true,"align":"right"},
                {"id":"payment","type":"field","x":15,"y":122,"w":180,"h":7,
                 "label":"付款方式：","field":"header.paymentTerms","fontSize":10,"align":"left"},
                {"id":"delivery-date","type":"field","x":15,"y":130,"w":90,"h":7,
                 "label":"交货日期：","field":"header.deliveryDate","fontSize":10,"align":"left"},
                {"id":"delivery-place","type":"field","x":15,"y":138,"w":180,"h":7,
                 "label":"交货地点：","field":"header.deliveryPlace","fontSize":10,"align":"left"},
                {"id":"terms","type":"field","x":15,"y":146,"w":180,"h":16,
                 "label":"其他约定：","field":"header.remark","fontSize":10,"align":"left"},
                {"id":"sign","type":"signature","x":15,"y":250,"w":180,"h":30,
                 "text":"甲方（盖章）：                                  乙方（盖章）：",
                 "fontSize":10,"align":"left","border":true}
              ]
            }
            """;

    private static final String DELIVERY_NOTE = """
            {
              "page": {"size":"A4","orientation":"PORTRAIT","marginMm":10,"gridMm":3},
              "components": [
                {"id":"title","type":"text","x":15,"y":10,"w":180,"h":12,
                 "text":"{{header.title}}","fontSize":20,"bold":true,"align":"center"},
                {"id":"company","type":"field","x":15,"y":24,"w":100,"h":7,
                 "field":"company.name","fontSize":10,"bold":true,"align":"left"},
                {"id":"no","type":"field","x":120,"y":24,"w":75,"h":7,
                 "label":"送货单号：","field":"header.no","fontSize":10,"align":"right"},
                {"id":"partner","type":"field","x":15,"y":33,"w":110,"h":7,
                 "label":"客户：","field":"partner.name","fontSize":10,"align":"left"},
                {"id":"date","type":"field","x":125,"y":33,"w":70,"h":7,
                 "label":"送货日期：","field":"header.dateLabel","fontSize":10,"align":"right"},
                {"id":"address","type":"field","x":15,"y":41,"w":110,"h":7,
                 "label":"收货地址：","field":"partner.address","fontSize":10,"align":"left"},
                {"id":"order","type":"field","x":125,"y":41,"w":70,"h":7,
                 "label":"关联订单：","field":"header.orderNo","fontSize":10,"align":"right"},
                {"id":"items","type":"table","x":15,"y":52,"w":180,"h":72,
                 "headerHeight":8,"rowHeight":7,"fontSize":9,"headerBold":true,"border":true,
                 "columns":[
                   {"title":"序号","field":"item.seq","width":12,"align":"center"},
                   {"title":"产品名称","field":"item.productName","width":45},
                   {"title":"规格型号","field":"item.spec","width":35},
                   {"title":"封装","field":"item.packageType","width":20},
                   {"title":"数量","field":"item.quantity","width":18,"align":"right"},
                   {"title":"单位","field":"item.unit","width":15,"align":"center"},
                   {"title":"备注","field":"item.remark","width":35}
                 ]},
                {"id":"quantity","type":"field","x":15,"y":130,"w":60,"h":7,
                 "label":"合计数量：","field":"totals.totalQuantity","fontSize":10,"align":"left"},
                {"id":"amount","type":"field","x":105,"y":130,"w":90,"h":7,
                 "label":"合计金额：","field":"totals.totalAmount","fontSize":10,"bold":true,"align":"right"},
                {"id":"receiver","type":"text","x":15,"y":200,"w":80,"h":12,
                 "text":"收货人签字：","fontSize":10,"align":"left","border":true},
                {"id":"driver","type":"text","x":115,"y":200,"w":80,"h":12,
                 "text":"送货人签字：","fontSize":10,"align":"left","border":true},
                {"id":"sign","type":"signature","x":15,"y":220,"w":180,"h":26,
                 "text":"客户签收（盖章）：                              公司盖章：",
                 "fontSize":10,"align":"left","border":true}
              ]
            }
            """;
}
