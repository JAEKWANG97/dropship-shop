import Link from "next/link";
import { redirect } from "next/navigation";
import { getAddresses, getProfileCompletion, getReferralState } from "@/lib/account";
import { getCurrentUser } from "@/lib/session";
import { AddressFields } from "../address-fields";
import { SubmitButton } from "../submit-button";
import {
  createAddress,
  deleteAddress,
  requestAccountDeletion,
  updateAddress,
  updateProfile,
} from "./actions";

type AccountPageProps = {
  searchParams: Promise<{ message?: string }>;
};

export default async function AccountPage({ searchParams }: AccountPageProps) {
  const params = await searchParams;
  const session = await getCurrentUser();

  if (!session) {
    redirect("/login?redirectTo=%2Faccount");
  }

  const [profile, referral, addresses] = await Promise.all([
    loadProfileCompletion(),
    loadReferralState(),
    loadAddresses(),
  ]);

  return (
    <section className="narrow-page">
      <p className="eyebrow">계정</p>
      <h1>내 계정</h1>
      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}
      <div className="notice success">
        <strong>로그인 상태</strong>
        <span>현재 계정으로 로그인되어 있습니다.</span>
      </div>
      {profile.error ? (
        <div className="notice danger">
          <strong>필수 정보를 불러오지 못했습니다</strong>
          <span>API 서버와 로그인 상태를 확인해 주세요.</span>
        </div>
      ) : (
        <>
          <div className={profile.data.requiredInfoComplete ? "notice success" : "notice"}>
            <strong>{profile.data.requiredInfoComplete ? "필수 정보 완료" : "필수 정보 필요"}</strong>
            <span>주문 전 이름, 연락 가능한 이메일, 배송 연락처가 필요합니다.</span>
          </div>
          <form action={updateProfile} className="account-form">
            <label>
              이름
              <input name="displayName" required defaultValue={profile.data.displayName} />
            </label>
            <label>
              이메일
              <input name="email" required type="email" defaultValue={profile.data.email} />
            </label>
            <label>
              배송 연락처
              <input
                name="phoneNumber"
                required
                inputMode="tel"
                placeholder="01012345678"
                defaultValue={profile.data.phoneNumber ?? ""}
              />
              <span className="field-help">주문 확인과 배송 연락에 사용할 휴대폰 번호를 입력해 주세요.</span>
            </label>
            <SubmitButton className="button" pendingLabel="저장 중...">
              기본 정보 저장
            </SubmitButton>
          </form>
        </>
      )}
      {referral.error ? (
        <div className="notice danger">
          <strong>추천 정보를 불러오지 못했습니다</strong>
          <span>로그인 상태를 확인한 뒤 다시 시도해 주세요.</span>
        </div>
      ) : (
        <section className="account-form">
          <h2>추천 코드</h2>
          <p className="field-help">지인에게 공유할 수 있는 내 고유 추천 코드입니다.</p>
          <dl className="summary-list">
            <div>
              <dt>내 추천 코드</dt>
              <dd>{referral.data.myReferralCode}</dd>
            </div>
            <div>
              <dt>추천인 등록</dt>
              <dd>{referral.data.referrerRegistered ? "등록됨" : "미등록"}</dd>
            </div>
          </dl>
          {!referral.data.referrerRegistered ? (
            <Link className="button" href="/welcome?redirectTo=/account">
              추천인 코드 등록
            </Link>
          ) : null}
        </section>
      )}
      <section className="account-form">
        <h2>배송지 관리</h2>
        {addresses.error ? (
          <div className="notice danger">
            <strong>배송지를 불러오지 못했습니다</strong>
            <span>로그인 상태를 확인한 뒤 다시 시도해 주세요.</span>
          </div>
        ) : (
          <>
            <div className="account-address-list">
              {addresses.data.addresses.length === 0 ? (
                <p className="field-help">저장된 배송지가 없습니다.</p>
              ) : (
                addresses.data.addresses.map((address) => (
                  <details className="account-address-editor" key={address.id}>
                    <summary>
                      <strong>{address.recipientName}</strong>
                      <span>
                        {address.address1} {address.address2}
                      </span>
                      {address.defaultAddress ? <em>기본 배송지</em> : null}
                    </summary>
                    <form action={updateAddress} className="account-address-form">
                      <input name="addressId" type="hidden" value={address.id} />
                      <label>
                        받는 사람
                        <input name="recipientName" required defaultValue={address.recipientName} />
                      </label>
                      <label>
                        연락처
                        <input
                          name="recipientPhone"
                          required
                          inputMode="tel"
                          defaultValue={address.recipientPhone}
                        />
                      </label>
                      <AddressFields
                        postalCode={address.postalCode}
                        address1={address.address1}
                        address2={address.address2 ?? ""}
                      />
                      <label className="checkbox-label">
                        <input
                          name="defaultAddress"
                          type="checkbox"
                          value="true"
                          defaultChecked={address.defaultAddress}
                        />
                        <span>기본 배송지로 사용</span>
                      </label>
                      <SubmitButton className="button" pendingLabel="수정 중...">
                        배송지 수정
                      </SubmitButton>
                    </form>
                    <form action={deleteAddress}>
                      <input name="addressId" type="hidden" value={address.id} />
                      <SubmitButton className="button secondary" pendingLabel="삭제 중...">
                        배송지 삭제
                      </SubmitButton>
                    </form>
                  </details>
                ))
              )}
            </div>
            <form action={createAddress} className="account-address-form">
              <h3>새 배송지</h3>
              <label>
                받는 사람
                <input
                  name="recipientName"
                  required
                  defaultValue={profile.error ? "" : profile.data.displayName}
                />
              </label>
              <label>
                연락처
                <input
                  name="recipientPhone"
                  required
                  inputMode="tel"
                  defaultValue={profile.error ? "" : (profile.data.phoneNumber ?? "")}
                />
              </label>
              <AddressFields />
              <label className="checkbox-label">
                <input name="defaultAddress" type="checkbox" value="true" defaultChecked />
                <span>기본 배송지로 사용</span>
              </label>
              <SubmitButton className="button" pendingLabel="저장 중...">
                배송지 저장
              </SubmitButton>
            </form>
          </>
        )}
      </section>
      <div className="link-list">
        <Link href="/products">상품 보기</Link>
        <Link href="/orders">주문 내역</Link>
      </div>
      <form action="/auth/logout" method="post">
        <SubmitButton className="button" pendingLabel="로그아웃 중...">
          로그아웃
        </SubmitButton>
      </form>
      <form action={requestAccountDeletion} className="account-form">
        <h2>회원 탈퇴</h2>
        <div className="notice danger">
          <strong>탈퇴 전 확인</strong>
          <span>
            탈퇴하면 로그인 정보와 연락처가 비식별화되고, 진행 중인 주문·환불·클레임이 있으면 처리할 수 없습니다.
            법정 보존이 필요한 거래 기록은 분쟁 대응과 법령 준수를 위해 보관됩니다.
          </span>
        </div>
        <label className="checkbox-label">
          <input name="confirmDeletion" required type="checkbox" value="yes" />
          <span>위 내용을 확인했으며 회원 탈퇴를 요청합니다.</span>
        </label>
        <SubmitButton className="button" pendingLabel="요청 중...">
          회원 탈퇴 요청
        </SubmitButton>
      </form>
    </section>
  );
}

async function loadProfileCompletion() {
  try {
    return { error: false as const, data: await getProfileCompletion() };
  } catch {
    return { error: true as const, data: null };
  }
}

async function loadReferralState() {
  try {
    return { error: false as const, data: await getReferralState() };
  } catch {
    return { error: true as const, data: null };
  }
}

async function loadAddresses() {
  try {
    return { error: false as const, data: await getAddresses() };
  } catch {
    return { error: true as const, data: null };
  }
}
