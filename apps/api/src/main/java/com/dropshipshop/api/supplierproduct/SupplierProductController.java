package com.dropshipshop.api.supplierproduct;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/supplier/products")
@PreAuthorize("hasRole('SUPPLIER')")
class SupplierProductController {

	private final SupplierProductService supplierProductService;
	private final SupplierInventoryService supplierInventoryService;
	private final CurrentUser currentUser;

	SupplierProductController(
		SupplierProductService supplierProductService,
		SupplierInventoryService supplierInventoryService,
		CurrentUser currentUser
	) {
		this.supplierProductService = supplierProductService;
		this.supplierInventoryService = supplierInventoryService;
		this.currentUser = currentUser;
	}

	@GetMapping
	SupplierProductDtos.ProductListResponse list(Authentication authentication) {
		return supplierProductService.list(currentUser.id(authentication));
	}

	@PostMapping
	ResponseEntity<SupplierProductDtos.ProductResponse> create(
		Authentication authentication,
		@Valid @RequestBody SupplierProductDtos.ProductCreateRequest request
	) {
		return created(supplierProductService.create(currentUser.id(authentication), request));
	}

	@GetMapping("/{productId}")
	ResponseEntity<SupplierProductDtos.ProductResponse> get(
		Authentication authentication,
		@PathVariable UUID productId
	) {
		return ok(supplierProductService.get(currentUser.id(authentication), productId));
	}

	@PatchMapping("/{productId}")
	ResponseEntity<SupplierProductDtos.ProductResponse> update(
		Authentication authentication,
		@PathVariable UUID productId,
		@Valid @RequestBody SupplierProductDtos.ProductUpdateRequest request
	) {
		return ok(supplierProductService.update(currentUser.id(authentication), productId, request));
	}

	@DeleteMapping("/{productId}")
	ResponseEntity<Void> delete(
		Authentication authentication,
		@PathVariable UUID productId,
		@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
	) {
		supplierProductService.delete(currentUser.id(authentication), productId, expectedVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{productId}/submit")
	ResponseEntity<SupplierProductDtos.ProductResponse> submit(
		Authentication authentication,
		@PathVariable UUID productId,
		@Valid @RequestBody SupplierProductDtos.VersionRequest request
	) {
		return ok(supplierProductService.submit(currentUser.id(authentication), productId, request.expectedVersion()));
	}

	@PostMapping("/{productId}/options")
	ResponseEntity<SupplierProductDtos.ProductResponse> createOption(
		Authentication authentication,
		@PathVariable UUID productId,
		@Valid @RequestBody SupplierProductDtos.OptionRequest request
	) {
		return created(supplierProductService.createOption(currentUser.id(authentication), productId, request));
	}

	@PatchMapping("/{productId}/options/{optionId}")
	ResponseEntity<SupplierProductDtos.ProductResponse> updateOption(
		Authentication authentication,
		@PathVariable UUID productId,
		@PathVariable UUID optionId,
		@Valid @RequestBody SupplierProductDtos.OptionRequest request
	) {
		return ok(supplierProductService.updateOption(currentUser.id(authentication), productId, optionId, request));
	}

	@PutMapping("/{productId}/options/{optionId}/inventory")
	ResponseEntity<SupplierProductDtos.InventoryResponse> updateInventory(
		Authentication authentication,
		@PathVariable UUID productId,
		@PathVariable UUID optionId,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierProductDtos.InventoryUpdateRequest request
	) {
		return ResponseEntity.ok(supplierInventoryService.update(
			currentUser.id(authentication), productId, optionId, idempotencyKey, request
		));
	}

	@DeleteMapping("/{productId}/options/{optionId}")
	ResponseEntity<Void> deleteOption(
		Authentication authentication,
		@PathVariable UUID productId,
		@PathVariable UUID optionId,
		@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
	) {
		long version = supplierProductService.deleteOption(
			currentUser.id(authentication),
			productId,
			optionId,
			expectedVersion(ifMatch)
		);
		return ResponseEntity.noContent().eTag(etag(version)).build();
	}

	@PostMapping("/{productId}/images")
	ResponseEntity<SupplierProductDtos.ProductResponse> uploadImage(
		Authentication authentication,
		@PathVariable UUID productId,
		@RequestPart("file") MultipartFile file,
		@RequestParam ProductImageType type,
		@RequestParam(required = false) String altText,
		@RequestParam long expectedVersion
	) {
		return created(supplierProductService.uploadImage(
			currentUser.id(authentication), productId, expectedVersion, type, altText, file
		));
	}

	@DeleteMapping("/{productId}/images/{imageId}")
	ResponseEntity<Void> deleteImage(
		Authentication authentication,
		@PathVariable UUID productId,
		@PathVariable UUID imageId,
		@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
	) {
		long version = supplierProductService.deleteImage(
			currentUser.id(authentication), productId, imageId, expectedVersion(ifMatch)
		);
		return ResponseEntity.noContent().eTag(etag(version)).build();
	}

	@PutMapping("/{productId}/images/order")
	ResponseEntity<SupplierProductDtos.ProductResponse> reorderImages(
		Authentication authentication,
		@PathVariable UUID productId,
		@Valid @RequestBody SupplierProductDtos.ImageOrderRequest request
	) {
		return ok(supplierProductService.reorderImages(currentUser.id(authentication), productId, request));
	}

	@PutMapping("/{productId}/detail-blocks")
	ResponseEntity<SupplierProductDtos.ProductResponse> replaceDetailBlocks(
		Authentication authentication,
		@PathVariable UUID productId,
		@Valid @RequestBody SupplierProductDtos.DetailBlocksRequest request
	) {
		return ok(supplierProductService.replaceDetailBlocks(currentUser.id(authentication), productId, request));
	}

	@PutMapping("/{productId}/notice")
	ResponseEntity<SupplierProductDtos.ProductResponse> updateNotice(
		Authentication authentication,
		@PathVariable UUID productId,
		@Valid @RequestBody SupplierProductDtos.NoticeRequest request
	) {
		return ok(supplierProductService.updateNotice(currentUser.id(authentication), productId, request));
	}

	private long expectedVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new ApiErrorException(
				HttpStatus.PRECONDITION_REQUIRED,
				ApiErrorCode.PRODUCT_VERSION_REQUIRED,
				"If-Match is required"
			);
		}
		String value = ifMatch.trim();
		if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 2) {
			value = value.substring(1, value.length() - 1);
		}
		try {
			long version = Long.parseLong(value);
			if (version < 0) {
				throw new NumberFormatException();
			}
			return version;
		} catch (NumberFormatException exception) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST, "Invalid If-Match");
		}
	}

	private ResponseEntity<SupplierProductDtos.ProductResponse> ok(SupplierProductDtos.ProductResponse response) {
		return ResponseEntity.ok().eTag(etag(response.version())).body(response);
	}

	private ResponseEntity<SupplierProductDtos.ProductResponse> created(SupplierProductDtos.ProductResponse response) {
		return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(response.version())).body(response);
	}

	private String etag(long version) {
		return "\"" + version + "\"";
	}
}
