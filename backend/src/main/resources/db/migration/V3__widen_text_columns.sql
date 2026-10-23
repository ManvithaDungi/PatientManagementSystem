-- V3: widen columns that overflowed (audit details and free-text clinical fields) and backfill last login.
alter table audit_logs alter column details set data type text;
alter table medical_records alter column diagnosis set data type varchar(2000);
alter table lab_tests alter column remarks set data type varchar(2000);

-- Archival now depends on last_login_at; give existing accounts a starting point so none are archived by mistake.
update login set last_login_at = now() where last_login_at is null;
