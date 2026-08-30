ALTER TABLE orders
	ADD CONSTRAINT uk_orders_id_supplier UNIQUE (id, supplier_id);

ALTER TABLE claims
	ADD CONSTRAINT uk_claims_id_order UNIQUE (id, order_id);

CREATE TABLE supplier_shortage_reports (
	id UUID PRIMARY KEY,
	order_id UUID NOT NULL,
	supplier_id UUID NOT NULL,
	actor_user_id UUID,
	reason_code VARCHAR(40) NOT NULL,
	status VARCHAR(20) NOT NULL,
	request_hash VARCHAR(128) NOT NULL,
	idempotency_key VARCHAR(200) NOT NULL,
	submit_result_snapshot JSONB NOT NULL,
	reviewed_by_admin_id UUID,
	reviewed_at TIMESTAMP WITH TIME ZONE,
	review_reason_code VARCHAR(40),
	review_request_hash VARCHAR(128),
	review_idempotency_key VARCHAR(200),
	review_result_snapshot JSONB,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT fk_supplier_shortage_reports_order
		FOREIGN KEY (order_id) REFERENCES orders(id),
	CONSTRAINT fk_supplier_shortage_reports_supplier
		FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
	CONSTRAINT fk_supplier_shortage_reports_order_supplier
		FOREIGN KEY (order_id, supplier_id) REFERENCES orders(id, supplier_id),
	CONSTRAINT fk_supplier_shortage_reports_actor
		FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
	CONSTRAINT fk_supplier_shortage_reports_reviewer
		FOREIGN KEY (reviewed_by_admin_id) REFERENCES users(id) ON DELETE SET NULL,
	CONSTRAINT uk_supplier_shortage_reports_order
		UNIQUE (order_id),
	CONSTRAINT uk_supplier_shortage_reports_supplier_key
		UNIQUE (supplier_id, idempotency_key),
	CONSTRAINT ck_supplier_shortage_reports_reason
		CHECK (reason_code IN ('OUT_OF_STOCK', 'OPTION_UNAVAILABLE', 'QUANTITY_UNAVAILABLE')),
	CONSTRAINT ck_supplier_shortage_reports_status
		CHECK (status IN ('REPORTED', 'APPROVED', 'REJECTED')),
	CONSTRAINT ck_supplier_shortage_reports_submit_snapshot
		CHECK (jsonb_typeof(submit_result_snapshot) = 'object'),
	CONSTRAINT ck_supplier_shortage_reports_review
		CHECK (
			(status = 'REPORTED'
				AND reviewed_by_admin_id IS NULL
				AND reviewed_at IS NULL
				AND review_reason_code IS NULL
				AND review_request_hash IS NULL
				AND review_idempotency_key IS NULL
				AND review_result_snapshot IS NULL)
			OR
			(status = 'APPROVED'
				AND reviewed_at IS NOT NULL
				AND review_reason_code = 'SHORTAGE_CONFIRMED'
				AND review_request_hash IS NOT NULL
				AND review_idempotency_key IS NOT NULL
				AND review_result_snapshot IS NOT NULL
				AND jsonb_typeof(review_result_snapshot) = 'object')
			OR
			(status = 'REJECTED'
				AND reviewed_at IS NOT NULL
				AND review_reason_code IN ('INSUFFICIENT_EVIDENCE', 'FULFILLMENT_CAN_CONTINUE')
				AND review_request_hash IS NOT NULL
				AND review_idempotency_key IS NOT NULL
				AND review_result_snapshot IS NOT NULL
				AND jsonb_typeof(review_result_snapshot) = 'object')
		)
);

CREATE INDEX idx_supplier_shortage_reports_supplier_created_desc
	ON supplier_shortage_reports(supplier_id, created_at DESC, id DESC);
CREATE INDEX idx_supplier_shortage_reports_status_created_desc
	ON supplier_shortage_reports(status, created_at DESC, id DESC);

CREATE TABLE supplier_claim_tasks (
	id UUID PRIMARY KEY,
	claim_id UUID NOT NULL,
	order_id UUID NOT NULL,
	supplier_id UUID NOT NULL,
	requested_type VARCHAR(40) NOT NULL,
	status VARCHAR(20) NOT NULL,
	instruction_code VARCHAR(40) NOT NULL,
	instructions VARCHAR(200) NOT NULL,
	requested_by_admin_id UUID,
	creation_request_hash VARCHAR(128) NOT NULL,
	creation_idempotency_key VARCHAR(200) NOT NULL,
	creation_result_snapshot JSONB NOT NULL,
	requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
	due_at TIMESTAMP WITH TIME ZONE NOT NULL,
	answered_at TIMESTAMP WITH TIME ZONE,
	closed_by_admin_id UUID,
	closed_at TIMESTAMP WITH TIME ZONE,
	close_reason_code VARCHAR(40),
	close_request_hash VARCHAR(128),
	close_idempotency_key VARCHAR(200),
	close_result_snapshot JSONB,
	CONSTRAINT fk_supplier_claim_tasks_claim
		FOREIGN KEY (claim_id) REFERENCES claims(id),
	CONSTRAINT fk_supplier_claim_tasks_claim_order
		FOREIGN KEY (claim_id, order_id) REFERENCES claims(id, order_id),
	CONSTRAINT fk_supplier_claim_tasks_supplier
		FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
	CONSTRAINT fk_supplier_claim_tasks_order_supplier
		FOREIGN KEY (order_id, supplier_id) REFERENCES orders(id, supplier_id),
	CONSTRAINT fk_supplier_claim_tasks_requester
		FOREIGN KEY (requested_by_admin_id) REFERENCES users(id) ON DELETE SET NULL,
	CONSTRAINT fk_supplier_claim_tasks_closer
		FOREIGN KEY (closed_by_admin_id) REFERENCES users(id) ON DELETE SET NULL,
	CONSTRAINT uk_supplier_claim_tasks_claim_key
		UNIQUE (claim_id, creation_idempotency_key),
	CONSTRAINT uk_supplier_claim_tasks_fact_scope
		UNIQUE (id, claim_id, supplier_id, requested_type),
	CONSTRAINT ck_supplier_claim_tasks_type
		CHECK (requested_type IN (
			'SHIPMENT_STOP_RESULT', 'RETURN_INSTRUCTIONS', 'RETURN_RECEIVED', 'INSPECTION_RESULT'
		)),
	CONSTRAINT ck_supplier_claim_tasks_status
		CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
	CONSTRAINT ck_supplier_claim_tasks_instruction
		CHECK (
			(requested_type = 'SHIPMENT_STOP_RESULT'
				AND instruction_code = 'CHECK_SHIPMENT_STOP'
				AND instructions = '상품 발송을 멈출 수 있는지 확인해 주세요.')
			OR (requested_type = 'RETURN_INSTRUCTIONS'
				AND instruction_code = 'PROVIDE_RETURN_METHOD'
				AND instructions = '반품 수거 방법을 선택해 주세요.')
			OR (requested_type = 'RETURN_RECEIVED'
				AND instruction_code = 'CONFIRM_RETURN_RECEIPT'
				AND instructions = '반품 상품 수령 여부를 확인해 주세요.')
			OR (requested_type = 'INSPECTION_RESULT'
				AND instruction_code = 'INSPECT_RETURNED_ITEM'
				AND instructions = '반품 상품의 상태를 확인해 주세요.')
		),
	CONSTRAINT ck_supplier_claim_tasks_due_at
		CHECK (requested_at < due_at AND due_at <= requested_at + INTERVAL '30 days'),
	CONSTRAINT ck_supplier_claim_tasks_creation_snapshot
		CHECK (jsonb_typeof(creation_result_snapshot) = 'object'),
	CONSTRAINT ck_supplier_claim_tasks_state
		CHECK (
			(status = 'OPEN'
				AND answered_at IS NULL
				AND closed_at IS NULL
				AND close_reason_code IS NULL
				AND closed_by_admin_id IS NULL
				AND close_request_hash IS NULL
				AND close_idempotency_key IS NULL
				AND close_result_snapshot IS NULL)
			OR (status = 'ANSWERED'
				AND answered_at IS NOT NULL
				AND closed_at IS NULL
				AND close_reason_code IS NULL
				AND closed_by_admin_id IS NULL
				AND close_request_hash IS NULL
				AND close_idempotency_key IS NULL
				AND close_result_snapshot IS NULL)
			OR (status = 'CLOSED'
				AND closed_at IS NOT NULL
				AND close_reason_code IS NOT NULL)
		),
	CONSTRAINT ck_supplier_claim_tasks_close_actor
		CHECK (
			status <> 'CLOSED'
			OR (close_reason_code IN ('RESPONSE_ACCEPTED', 'SUPERSEDED', 'NO_LONGER_NEEDED')
				AND close_request_hash IS NOT NULL
				AND close_idempotency_key IS NOT NULL
				AND close_result_snapshot IS NOT NULL
				AND jsonb_typeof(close_result_snapshot) = 'object')
			OR (close_reason_code IN ('DUE_AT_EXPIRED', 'CLAIM_TERMINAL')
				AND closed_by_admin_id IS NULL
				AND close_request_hash IS NULL
				AND close_idempotency_key IS NULL
				AND close_result_snapshot IS NULL)
		)
);

CREATE INDEX idx_supplier_claim_tasks_supplier_requested_desc
	ON supplier_claim_tasks(supplier_id, requested_at DESC, id DESC);
CREATE INDEX idx_supplier_claim_tasks_claim_requested_desc
	ON supplier_claim_tasks(claim_id, requested_at DESC, id DESC);
CREATE INDEX idx_supplier_claim_tasks_status_requested_desc
	ON supplier_claim_tasks(status, requested_at DESC, id DESC);
CREATE INDEX idx_supplier_claim_tasks_due_candidates
	ON supplier_claim_tasks(due_at, id)
	WHERE status IN ('OPEN', 'ANSWERED');

CREATE TABLE supplier_claim_facts (
	id UUID PRIMARY KEY,
	task_id UUID NOT NULL,
	claim_id UUID NOT NULL,
	supplier_id UUID NOT NULL,
	actor_user_id UUID,
	type VARCHAR(40) NOT NULL,
	payload JSONB NOT NULL,
	corrects_fact_id UUID,
	request_hash VARCHAR(128) NOT NULL,
	idempotency_key VARCHAR(200) NOT NULL,
	result_snapshot JSONB NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT fk_supplier_claim_facts_task_scope
		FOREIGN KEY (task_id, claim_id, supplier_id, type)
		REFERENCES supplier_claim_tasks(id, claim_id, supplier_id, requested_type),
	CONSTRAINT fk_supplier_claim_facts_actor
		FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
	CONSTRAINT uk_supplier_claim_facts_task_key
		UNIQUE (task_id, idempotency_key),
	CONSTRAINT uk_supplier_claim_facts_correction_scope
		UNIQUE (id, task_id, type),
	CONSTRAINT fk_supplier_claim_facts_correction_scope
		FOREIGN KEY (corrects_fact_id, task_id, type)
		REFERENCES supplier_claim_facts(id, task_id, type),
	CONSTRAINT ck_supplier_claim_facts_type
		CHECK (type IN (
			'SHIPMENT_STOP_RESULT', 'RETURN_INSTRUCTIONS', 'RETURN_RECEIVED', 'INSPECTION_RESULT'
		)),
	CONSTRAINT ck_supplier_claim_facts_payload
		CHECK (jsonb_typeof(payload) = 'object'),
	CONSTRAINT ck_supplier_claim_facts_result_snapshot
		CHECK (jsonb_typeof(result_snapshot) = 'object'),
	CONSTRAINT ck_supplier_claim_facts_not_self_correction
		CHECK (corrects_fact_id IS NULL OR corrects_fact_id <> id)
);

CREATE INDEX idx_supplier_claim_facts_task_created
	ON supplier_claim_facts(task_id, created_at, id);
CREATE INDEX idx_supplier_claim_facts_corrects_fact
	ON supplier_claim_facts(corrects_fact_id)
	WHERE corrects_fact_id IS NOT NULL;
CREATE UNIQUE INDEX uk_supplier_claim_facts_single_root
	ON supplier_claim_facts(task_id)
	WHERE corrects_fact_id IS NULL;
CREATE UNIQUE INDEX uk_supplier_claim_facts_single_child
	ON supplier_claim_facts(corrects_fact_id)
	WHERE corrects_fact_id IS NOT NULL;
