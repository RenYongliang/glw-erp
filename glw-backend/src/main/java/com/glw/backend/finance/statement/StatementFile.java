package com.glw.backend.finance.statement;

/** 导出结果：文件名、MIME 类型和字节内容。 */
public record StatementFile(String fileName, String contentType, byte[] content) {}
