-- V1: baseline schema.
--
-- Generated from the JPA entities (Hibernate schema export, PostgreSQL dialect)
-- so that it is exactly what the application expects. From here on the schema
-- changes ONLY through new, numbered migrations (V2__..., V3__...); never edit
-- this file once it has been applied anywhere.
--
-- Guarantees enforced by the database itself:
--   * appointments.active_slot_key UNIQUE -> no double booking (NULL once cancelled/rejected)
--   * patient_consents.active_key  UNIQUE -> at most one ACTIVE grant per patient/provider/type
--   * secondary indexes for the appointment, clinical-record, audit and task query paths

create table appointments (appointment_date timestamp(6) not null, appointment_id bigserial not null, created_at timestamp(6), doctor_id bigint not null, patient_profile_id bigint not null, active_slot_key varchar(255) unique, appointment_type varchar(255), cancellation_reason TEXT, doctor_notes TEXT, reason_for_visit TEXT, special_requirements TEXT, status varchar(255) not null check (status in ('PENDING_APPROVAL','SCHEDULED','COMPLETED','CANCELLED','NO_SHOW','REJECTED')), primary key (appointment_id));
create table archived_users (archived_at timestamp(6), id bigserial not null, last_active_at timestamp(6), original_user_id bigint not null, email varchar(255) not null, reason TEXT, role varchar(255) not null check (role in ('PATIENT','DOCTOR','NURSE','ADMIN','LAB_TECHNICIAN')), primary key (id));
create table audit_logs (id bigserial not null, timestamp timestamp(6) not null, action varchar(255) not null, details varchar(255), email varchar(255) not null, ip_address varchar(255), user_agent varchar(255), primary key (id));
create table doctor_profiles (shift_end_time time(6) not null, shift_start_time time(6) not null, slot_duration_minutes integer not null, profile_id bigserial not null, user_id bigint not null unique, contact_number varchar(255), department varchar(255), first_name varchar(255) not null, last_name varchar(255) not null, specialty varchar(255) not null, primary key (profile_id));
create table doctor_working_days (doctor_profile_id bigint not null, working_days varchar(255) check (working_days in ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')));
create table handover_notes (is_read boolean not null, author_id bigint not null, id bigserial not null, patient_id bigint, timestamp timestamp(6) not null, content TEXT not null, priority varchar(255) not null, shift_direction varchar(255), type varchar(255) not null, primary key (id));
create table lab_tests (ordered_at timestamp(6), ordered_by_id bigint not null, patient_profile_id bigint not null, test_id bigserial not null, file_url varchar(255), reference_range varchar(255), remarks varchar(255), result_value varchar(255), status varchar(255), test_category varchar(255), test_name varchar(255), unit varchar(255), primary key (test_id));
create table login (archived boolean default false, failed_attempts integer not null, is_locked boolean not null, two_factor_enabled boolean not null, created_at timestamp(6), last_login_at timestamp(6), lockout_until timestamp(6), otp_expiry timestamp(6), user_id bigserial not null, email varchar(255) not null unique, otp varchar(255), password_hash varchar(255) not null, role varchar(255) not null check (role in ('PATIENT','DOCTOR','NURSE','ADMIN','LAB_TECHNICIAN')), primary key (user_id));
create table medical_records (created_at timestamp(6), doctor_id bigint not null, patient_profile_id bigint not null, record_id bigserial not null, updated_at timestamp(6), attachment_url varchar(255), diagnosis varchar(255) not null, notes TEXT, symptoms TEXT, treatment_provided TEXT, primary key (record_id));
create table medication_administrations (administered_at timestamp(6) not null, id bigserial not null, nurse_id bigint not null, patient_profile_id bigint not null, prescription_id bigint not null, notes TEXT, primary key (id));
create table nurse_tasks (completed boolean not null, assigned_nurse_id bigint not null, due_time timestamp(6) not null, id bigserial not null, patient_id bigint, category varchar(255) not null, description TEXT, previous_status varchar(255), priority varchar(255) not null, status varchar(255), title varchar(255) not null, primary key (id));
create table password_history (created_at timestamp(6), id bigserial not null, user_id bigint not null, password_hash varchar(255) not null, primary key (id));
create table password_reset_tokens (used boolean not null, created_at timestamp(6), expires_at timestamp(6) not null, id bigserial not null, user_id bigint not null, token_hash varchar(255) not null unique, primary key (id));
create table patient_consents (expires_at timestamp(6), granted_at timestamp(6), granted_to_id bigint not null, id bigserial not null, patient_id bigint not null, revoked_at timestamp(6), active_key varchar(255) unique, consent_type varchar(255) not null, reason TEXT, status varchar(255) not null, primary key (id));
create table patient_profiles (date_of_birth date not null, assigned_doctor_id bigint, assigned_nurse_id bigint, created_at timestamp(6), profile_id bigserial not null, user_id bigint not null unique, address TEXT, contact_number varchar(255), first_name varchar(255) not null, gender varchar(255), last_name varchar(255) not null, medical_history TEXT, primary key (profile_id));
create table prescriptions (refills_remaining integer, doctor_id bigint not null, end_date timestamp(6), issued_at timestamp(6), patient_profile_id bigint not null, prescription_id bigserial not null, start_date timestamp(6), dosage varchar(255) not null, duration varchar(255) not null, frequency varchar(255) not null, medication_name varchar(255) not null, route varchar(255), special_instructions TEXT, status varchar(255), primary key (prescription_id));
create table sessions (revoked boolean not null, created_at timestamp(6) not null, expires_at timestamp(6) not null, id bigserial not null, user_id bigint not null, ip_address varchar(255), refresh_token_hash varchar(255) not null unique, user_agent varchar(255), primary key (id));
create table staff_invites (revoked boolean not null, used boolean not null, created_at timestamp(6), expires_at timestamp(6) not null, id bigserial not null, invited_by_id bigint, email varchar(255) not null, role varchar(255) not null check (role in ('PATIENT','DOCTOR','NURSE','ADMIN','LAB_TECHNICIAN')), token_hash varchar(255) not null unique, primary key (id));
create table uploaded_files (id bigserial not null, uploaded_at timestamp(6), uploaded_by_id bigint not null, filename varchar(255) not null unique, primary key (id));
create table vital_signs (heart_rate integer, height float(53), oxygen_saturation integer, respiratory_rate integer, temperature float(53), weight float(53), nurse_id bigint not null, patient_profile_id bigint not null, recorded_at timestamp(6), vital_id bigserial not null, blood_pressure varchar(255), primary key (vital_id));
create index idx_appointments_doctor_date on appointments (doctor_id, appointment_date);
create index idx_appointments_patient_date on appointments (patient_profile_id, appointment_date);
create index idx_appointments_status on appointments (status);
create index idx_audit_logs_timestamp on audit_logs (timestamp);
create index idx_audit_logs_email_timestamp on audit_logs (email, timestamp);
create index idx_handover_direction_ts on handover_notes (shift_direction, timestamp);
create index idx_lab_tests_patient on lab_tests (patient_profile_id);
create index idx_lab_tests_status_ordered on lab_tests (status, ordered_at);
create index idx_medical_records_patient on medical_records (patient_profile_id);
create index idx_nurse_tasks_nurse_completed_due on nurse_tasks (assigned_nurse_id, completed, due_time);
create index idx_consents_patient on patient_consents (patient_id, status);
create index idx_consents_provider on patient_consents (granted_to_id, status);
create index idx_prescriptions_patient_status on prescriptions (patient_profile_id, status);
create index idx_prescriptions_doctor on prescriptions (doctor_id);
create index idx_sessions_user_revoked on sessions (user_id, revoked);
create index idx_staff_invites_email on staff_invites (email);
create index idx_vital_signs_patient_recorded on vital_signs (patient_profile_id, recorded_at);
alter table if exists appointments add constraint FKt5r10ysaochap5vdlomm6dr9o foreign key (doctor_id) references login;
alter table if exists appointments add constraint FKqx253hq86d4bk773wiiye6jic foreign key (patient_profile_id) references patient_profiles;
alter table if exists doctor_profiles add constraint FKas928vsn4w54rsuo06wy2rv59 foreign key (user_id) references login;
alter table if exists doctor_working_days add constraint FK3bbb86ndmv21r8aolf1v7ivq9 foreign key (doctor_profile_id) references doctor_profiles;
alter table if exists handover_notes add constraint FK9sr96mran4fpaephb3o1qhnbp foreign key (author_id) references login;
alter table if exists handover_notes add constraint FKfy5datx6nhcdss3ndo8pagw69 foreign key (patient_id) references patient_profiles;
alter table if exists lab_tests add constraint FK4l2l1yl8gcvrod10jcy8d11c1 foreign key (ordered_by_id) references login;
alter table if exists lab_tests add constraint FKtc9230ve52sny3l273jr7qy3d foreign key (patient_profile_id) references patient_profiles;
alter table if exists medical_records add constraint FKsg4xe22bkvjfo8jvhf7p0g93x foreign key (doctor_id) references login;
alter table if exists medical_records add constraint FKce9nflak38ovrfo0679kxwnbt foreign key (patient_profile_id) references patient_profiles;
alter table if exists medication_administrations add constraint FKwfnhupe0cykwcu6cb367gund foreign key (nurse_id) references login;
alter table if exists medication_administrations add constraint FKq64crjv9u3fo3j2tsapfsy21 foreign key (patient_profile_id) references patient_profiles;
alter table if exists medication_administrations add constraint FKqc9xhujt59k9h0m4q5nm04r1k foreign key (prescription_id) references prescriptions;
alter table if exists nurse_tasks add constraint FKbg5w91kbqmfy4npmt0r6kebq1 foreign key (assigned_nurse_id) references login;
alter table if exists nurse_tasks add constraint FK539741aehlvgpphtbny0tl5bx foreign key (patient_id) references patient_profiles;
alter table if exists password_history add constraint FKt9ih2yq1iw02as2bamrxwl1h foreign key (user_id) references login;
alter table if exists password_reset_tokens add constraint FK7ulcn3t674bd39mqtr6isx0dt foreign key (user_id) references login;
alter table if exists patient_consents add constraint FKs614dld6n3k8nxsug31gg5q74 foreign key (granted_to_id) references login;
alter table if exists patient_consents add constraint FKp9e9ucd415slfj67eak8vbnp9 foreign key (patient_id) references patient_profiles;
alter table if exists patient_profiles add constraint FK38mb8bbt74q51408coiatkmx9 foreign key (assigned_doctor_id) references login;
alter table if exists patient_profiles add constraint FKh9lpjec791syj8ahpdsbwwt4l foreign key (assigned_nurse_id) references login;
alter table if exists patient_profiles add constraint FKdlc7l7tkkcevm0ad7hymfqsuo foreign key (user_id) references login;
alter table if exists prescriptions add constraint FKnkg1rhr183s92jr4jyaxnplug foreign key (doctor_id) references login;
alter table if exists prescriptions add constraint FKthgbqge6fasnoakwvjcujtjfq foreign key (patient_profile_id) references patient_profiles;
alter table if exists sessions add constraint FKmyximpkolo1oiyocx9lcpfym7 foreign key (user_id) references login;
alter table if exists staff_invites add constraint FKid12jgmn2936k0vbk8hc37rmv foreign key (invited_by_id) references login;
alter table if exists uploaded_files add constraint FKo2ti9ll6phuxsqr7d6t38u52p foreign key (uploaded_by_id) references login;
alter table if exists vital_signs add constraint FKkyisykfjwc3ewdeop99th9wuc foreign key (nurse_id) references login;
alter table if exists vital_signs add constraint FKjen08cu0342t5e5d5sfu0dctt foreign key (patient_profile_id) references patient_profiles;
