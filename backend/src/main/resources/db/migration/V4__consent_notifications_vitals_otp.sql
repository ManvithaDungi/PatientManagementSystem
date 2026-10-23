-- V4: consent withdrawal reason, in-app notifications, richer vital signs, pending-2FA token.

alter table patient_consents add column revocation_reason text;

alter table vital_signs add column pain_level integer;
alter table vital_signs add column notes text;
alter table vital_signs add column temperature_route varchar(30);

-- A login attempt that needs a second factor is bound to this (hashed) one-time pending token.
alter table login add column otp_pending_token_hash varchar(255);

create table notifications (
    id bigserial not null,
    user_id bigint not null,
    type varchar(50) not null,
    title varchar(200) not null,
    message text,
    created_at timestamp(6) not null,
    is_read boolean not null default false,
    primary key (id)
);
alter table notifications add constraint fk_notifications_user foreign key (user_id) references login;
create index idx_notifications_user_created on notifications (user_id, created_at);
