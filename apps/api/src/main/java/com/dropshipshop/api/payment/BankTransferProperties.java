package com.dropshipshop.api.payment;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BankTransferProperties {

	private final String bankName;
	private final String accountNumber;
	private final String accountHolder;
	private final long depositDeadlineHours;
	private final String cashReceiptNotice;

	public BankTransferProperties(
		@Value("${app.bank-transfer.bank-name:입금 계좌 준비중}") String bankName,
		@Value("${app.bank-transfer.account-number:입금 계좌 준비중}") String accountNumber,
		@Value("${app.bank-transfer.account-holder:가라사니}") String accountHolder,
		@Value("${app.bank-transfer.deposit-deadline-hours:24}") long depositDeadlineHours,
		@Value("${app.bank-transfer.cash-receipt-notice:현금영수증은 요청 시 홈택스에서 발급하며, 의무발행 대상 거래는 요청 여부와 관계없이 발급합니다.}") String cashReceiptNotice
	) {
		this.bankName = bankName;
		this.accountNumber = accountNumber;
		this.accountHolder = accountHolder;
		this.depositDeadlineHours = depositDeadlineHours;
		this.cashReceiptNotice = cashReceiptNotice;
	}

	public String bankName() {
		return bankName;
	}

	public String accountNumber() {
		return accountNumber;
	}

	public String accountHolder() {
		return accountHolder;
	}

	public Duration depositDeadline() {
		return Duration.ofHours(depositDeadlineHours);
	}

	public String cashReceiptNotice() {
		return cashReceiptNotice;
	}
}
