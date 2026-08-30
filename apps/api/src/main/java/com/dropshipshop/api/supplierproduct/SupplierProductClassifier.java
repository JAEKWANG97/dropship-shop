package com.dropshipshop.api.supplierproduct;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewReasonCode;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;

@Component
class SupplierProductClassifier {

	private static final Set<ProductCategory> AUTO_CATEGORIES = EnumSet.of(
		ProductCategory.PPE_SAFETY_HELMET,
		ProductCategory.PPE_SAFETY_SHOES,
		ProductCategory.PPE_FALL_ARREST_HARNESS,
		ProductCategory.PPE_SAFETY_BELT,
		ProductCategory.PPE_SAFETY_GLASSES,
		ProductCategory.PPE_RESPIRATOR,
		ProductCategory.PPE_EAR_PROTECTION,
		ProductCategory.PPE_WORK_GLOVES,
		ProductCategory.PPE_INSULATED_GLOVES,
		ProductCategory.PPE_WELDING_GLOVES,
		ProductCategory.PPE_HIGH_VISIBILITY_VEST,
		ProductCategory.PPE_PROTECTIVE_CLOTHING,
		ProductCategory.FALL_PREVENTION_NET,
		ProductCategory.FALLING_OBJECT_NET,
		ProductCategory.OPENING_COVER,
		ProductCategory.WORK_PLATFORM,
		ProductCategory.SAFETY_BLOCK,
		ProductCategory.SAFETY_SIGN,
		ProductCategory.WARNING_SIGN,
		ProductCategory.TRAFFIC_CONE,
		ProductCategory.SAFETY_FENCE,
		ProductCategory.BARRICADE,
		ProductCategory.WARNING_LIGHT,
		ProductCategory.SIGNAL_BATON,
		ProductCategory.BARRIER_TAPE,
		ProductCategory.GAS_DETECTOR,
		ProductCategory.OXYGEN_METER,
		ProductCategory.NOISE_METER,
		ProductCategory.LIGHT_METER,
		ProductCategory.ANEMOMETER,
		ProductCategory.VIBRATION_METER,
		ProductCategory.THERMAL_CAMERA_INSPECTION,
		ProductCategory.FIRST_AID_KIT,
		ProductCategory.FIRST_AID_SUPPLIES
	);

	private static final Set<ProductCategory> MANUAL_CATEGORIES = EnumSet.of(
		ProductCategory.FALL_PREVENTION_GUARDRAIL,
		ProductCategory.SAFE_PASSAGE,
		ProductCategory.LIFELINE,
		ProductCategory.DUST_METER,
		ProductCategory.AED,
		ProductCategory.EYEWASH_STATION,
		ProductCategory.HEAT_COLD_PREVENTION_SUPPLIES,
		ProductCategory.VENTILATION_EQUIPMENT,
		ProductCategory.SMART_SAFETY_HELMET,
		ProductCategory.SMART_SAFETY_VEST,
		ProductCategory.SMART_SAFETY_HARNESS,
		ProductCategory.SMART_WATCH,
		ProductCategory.SMART_CCTV_MOBILE,
		ProductCategory.SMART_CCTV_SOLAR_MOBILE,
		ProductCategory.SMART_CCTV_PTZ,
		ProductCategory.SMART_CCTV_THERMAL,
		ProductCategory.SMART_CCTV_DUAL_SPECTRUM,
		ProductCategory.WORKER_LOCATION_TRACKING,
		ProductCategory.WORKER_ACCESS_CONTROL,
		ProductCategory.WORKER_SOS_EMERGENCY_CALL,
		ProductCategory.HEAVY_EQUIPMENT_PROXIMITY_ALARM,
		ProductCategory.HEAVY_EQUIPMENT_COLLISION_PREVENTION,
		ProductCategory.HEAVY_EQUIPMENT_REAR_DETECTOR,
		ProductCategory.HEAVY_EQUIPMENT_PINCH_PREVENTION,
		ProductCategory.CRANE_PROXIMITY_ALARM,
		ProductCategory.OPENING_PROXIMITY_ALARM,
		ProductCategory.FALL_DETECTION_SYSTEM,
		ProductCategory.SCAFFOLD_DISPLACEMENT_MONITORING,
		ProductCategory.RETAINING_WALL_MEASUREMENT_SYSTEM,
		ProductCategory.IOT_GAS_DETECTOR,
		ProductCategory.IOT_DUST_METER,
		ProductCategory.IOT_NOISE_METER,
		ProductCategory.IOT_TEMPERATURE_HUMIDITY_METER,
		ProductCategory.IOT_ANEMOMETER,
		ProductCategory.DANGER_AREA_BARRIER,
		ProductCategory.ACCESS_CONTROL_FACILITY,
		ProductCategory.SMART_GAS_DETECTOR,
		ProductCategory.SMART_CCTV_AI_VIDEO_ANALYTICS,
		ProductCategory.SMART_CCTV_AI_SAFETY_MANAGEMENT,
		ProductCategory.SMART_CCTV_AI_HELMET_DETECTION,
		ProductCategory.SMART_CCTV_AI_VEST_DETECTION,
		ProductCategory.SMART_CCTV_AI_DANGER_ZONE_INTRUSION,
		ProductCategory.SMART_CCTV_AI_FALL_RISK_DETECTION,
		ProductCategory.SMART_CCTV_AI_FALLEN_WORKER_DETECTION,
		ProductCategory.SMART_CCTV_AI_FIRE_SMOKE_DETECTION,
		ProductCategory.SMART_CCTV_GENERAL_SPECIAL,
		ProductCategory.WORKER_LOCATION_ACCESS_MANAGEMENT,
		ProductCategory.WORKER_ELECTRONIC_ACCESS_CONTROL
	);

	private static final Set<ProductCategory> KOSHA_CATEGORY_CODES = EnumSet.of(
		ProductCategory.PPE_SAFETY_HELMET,
		ProductCategory.PPE_SAFETY_SHOES,
		ProductCategory.PPE_FALL_ARREST_HARNESS,
		ProductCategory.PPE_SAFETY_BELT,
		ProductCategory.PPE_SAFETY_GLASSES,
		ProductCategory.PPE_RESPIRATOR,
		ProductCategory.PPE_EAR_PROTECTION,
		ProductCategory.PPE_INSULATED_GLOVES,
		ProductCategory.PPE_PROTECTIVE_CLOTHING,
		ProductCategory.SMART_SAFETY_HELMET,
		ProductCategory.SMART_SAFETY_HARNESS,
		ProductCategory.FALL_PREVENTION_GUARDRAIL,
		ProductCategory.WORK_PLATFORM
	);

	Classification classify(
		Product product,
		boolean hasThumbnail,
		boolean hasActiveOption,
		boolean hasNotice,
		boolean supplementationResubmission
	) {
		try {
			if (product.getName() == null || product.getName().isBlank()
				|| product.getSummary() == null || product.getSummary().isBlank()
				|| product.getSourcePrice() < 0 || product.getBasePrice() <= 0
				|| product.getMinimumOrderQuantity() < 1
				|| product.getOrderQuantityStep() < 1 || !hasThumbnail || !hasActiveOption || !hasNotice) {
				return review(ProductReviewReasonCode.REQUIRED_INFO_MISSING);
			}
			ProductCategory category = product.getCategoryCode();
			if (category == null) {
				return review(ProductReviewReasonCode.SAFETY_REVIEW);
			}
			if (KOSHA_CATEGORY_CODES.contains(category)
				&& product.getComplianceStatus() != ProductComplianceStatus.VERIFIED
				&& product.getComplianceStatus() != ProductComplianceStatus.NOT_REQUIRED) {
				return review(ProductReviewReasonCode.CERTIFICATION_REVIEW);
			}
			if (product.getComplianceStatus() == ProductComplianceStatus.REJECTED) {
				return review(ProductReviewReasonCode.SAFETY_REVIEW);
			}
			if (supplementationResubmission) {
				return review(ProductReviewReasonCode.SAFETY_REVIEW);
			}
			if (MANUAL_CATEGORIES.contains(category)) {
				return review(ProductReviewReasonCode.CATEGORY_REVIEW);
			}
			if (AUTO_CATEGORIES.contains(category)) {
				return new Classification(ProductReviewStatus.AUTO_APPROVED, null);
			}
			return review(ProductReviewReasonCode.SAFETY_REVIEW);
		} catch (RuntimeException exception) {
			return review(ProductReviewReasonCode.SAFETY_REVIEW);
		}
	}

	private Classification review(ProductReviewReasonCode reasonCode) {
		return new Classification(ProductReviewStatus.REVIEW_REQUIRED, reasonCode);
	}

	record Classification(ProductReviewStatus status, ProductReviewReasonCode reasonCode) {
	}
}
