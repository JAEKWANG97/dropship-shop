import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";

export type AgreementState = {
  requiredAgreed: boolean;
  requiredTermsVersion: string;
  requiredPrivacyVersion: string;
  agreedTermsVersion: string | null;
  agreedPrivacyVersion: string | null;
  agreedAt: string | null;
};

export type Address = {
  id: string;
  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address1: string;
  address2: string | null;
  defaultAddress: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AddressList = {
  addresses: Address[];
};

export type ProfileCompletion = {
  displayName: string;
  displayNameComplete: boolean;
  email: string;
  emailRequired: boolean;
  emailComplete: boolean;
  phoneNumber: string | null;
  phoneVerified: boolean;
  phoneVerifiedAt: string | null;
  requiredInfoComplete: boolean;
};

export type ReferralState = {
  myReferralCode: string;
  referrerRegistered: boolean;
};

export async function getAgreementState() {
  return apiGetWithCookie<AgreementState>(
    "/api/me/agreements",
    (await cookies()).toString(),
  );
}

export async function getProfileCompletion() {
  return apiGetWithCookie<ProfileCompletion>(
    "/api/me/profile-completion",
    (await cookies()).toString(),
  );
}

export async function getReferralState() {
  return apiGetWithCookie<ReferralState>(
    "/api/me/referral",
    (await cookies()).toString(),
  );
}

export async function getAddresses() {
  return apiGetWithCookie<AddressList>(
    "/api/me/addresses",
    (await cookies()).toString(),
  );
}
