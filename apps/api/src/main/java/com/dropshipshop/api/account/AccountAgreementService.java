package com.dropshipshop.api.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.account.domain.UserPolicyAgreement;
import com.dropshipshop.api.account.repository.UserPolicyAgreementRepository;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class AccountAgreementService {

	private final UserPolicyAgreementRepository userPolicyAgreementRepository;
	private final UserAccountRepository userAccountRepository;
	private final AccountAgreementProperties accountAgreementProperties;
	private final Clock clock;

	AccountAgreementService(
		UserPolicyAgreementRepository userPolicyAgreementRepository,
		UserAccountRepository userAccountRepository,
		AccountAgreementProperties accountAgreementProperties
	) {
		this.userPolicyAgreementRepository = userPolicyAgreementRepository;
		this.userAccountRepository = userAccountRepository;
		this.accountAgreementProperties = accountAgreementProperties;
		this.clock = Clock.systemUTC();
	}

	@Transactional(readOnly = true)
	public AccountAgreementDtos.AgreementStateResponse getAgreementState(UUID userId) {
		Optional<UserPolicyAgreement> latestAgreement = latestAgreement(userId);
		return latestAgreement
			.map(this::toStateResponse)
			.orElseGet(() -> new AccountAgreementDtos.AgreementStateResponse(
				false,
				accountAgreementProperties.requiredTermsVersion(),
				accountAgreementProperties.requiredPrivacyVersion(),
				null,
				null,
				null
			));
	}

	@Transactional
	public AccountAgreementDtos.AgreeResponse agree(UUID userId, AccountAgreementDtos.AgreeRequest request) {
		validateRequiredVersions(request.termsVersion(), request.privacyVersion());
		Optional<UserPolicyAgreement> existing = userPolicyAgreementRepository
			.findByUser_IdAndTermsVersionAndPrivacyVersion(userId, request.termsVersion(), request.privacyVersion());
		if (existing.isPresent()) {
			return toAgreeResponse(existing.get());
		}

		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		UserPolicyAgreement agreement = userPolicyAgreementRepository.save(new UserPolicyAgreement(
			user,
			request.termsVersion(),
			request.privacyVersion(),
			Instant.now(clock)
		));
		return toAgreeResponse(agreement);
	}

	@Transactional(readOnly = true)
	public void requireCurrentAgreement(UUID userId) {
		if (!hasCurrentAgreement(userId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required account agreements are missing");
		}
	}

	@Transactional(readOnly = true)
	public boolean hasCurrentAgreement(UUID userId) {
		return userPolicyAgreementRepository.existsByUser_IdAndTermsVersionAndPrivacyVersion(
			userId,
			accountAgreementProperties.requiredTermsVersion(),
			accountAgreementProperties.requiredPrivacyVersion()
		);
	}

	private Optional<UserPolicyAgreement> latestAgreement(UUID userId) {
		return userPolicyAgreementRepository.findFirstByUser_IdOrderByAgreedAtDesc(userId);
	}

	private void validateRequiredVersions(String termsVersion, String privacyVersion) {
		if (!accountAgreementProperties.requiredTermsVersion().equals(termsVersion)
			|| !accountAgreementProperties.requiredPrivacyVersion().equals(privacyVersion)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agreement versions are not current");
		}
	}

	private AccountAgreementDtos.AgreementStateResponse toStateResponse(UserPolicyAgreement agreement) {
		return new AccountAgreementDtos.AgreementStateResponse(
			isCurrent(agreement),
			accountAgreementProperties.requiredTermsVersion(),
			accountAgreementProperties.requiredPrivacyVersion(),
			agreement.getTermsVersion(),
			agreement.getPrivacyVersion(),
			agreement.getAgreedAt()
		);
	}

	private AccountAgreementDtos.AgreeResponse toAgreeResponse(UserPolicyAgreement agreement) {
		return new AccountAgreementDtos.AgreeResponse(
			agreement.getId(),
			isCurrent(agreement),
			agreement.getTermsVersion(),
			agreement.getPrivacyVersion(),
			agreement.getAgreedAt()
		);
	}

	private boolean isCurrent(UserPolicyAgreement agreement) {
		return accountAgreementProperties.requiredTermsVersion().equals(agreement.getTermsVersion())
			&& accountAgreementProperties.requiredPrivacyVersion().equals(agreement.getPrivacyVersion());
	}
}
