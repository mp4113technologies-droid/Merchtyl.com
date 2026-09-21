package com.merchtyl.receipts;

public record KitchenPrintDispatchDto(KitchenPrintJobDto job, KitchenTicketDto ticket, String printerRole, int copies) {}
