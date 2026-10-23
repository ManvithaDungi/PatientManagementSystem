-- V6: earlier front-ends sent the admin's rejection reason as a JSON string, so it was stored wrapped in double quotes
-- (e.g. "Doctor unavailable" including the quote characters). Remove one pair of surrounding quotes.
update appointments
set cancellation_reason = substring(cancellation_reason from 2 for char_length(cancellation_reason) - 2)
where cancellation_reason like '"%"'
  and char_length(cancellation_reason) >= 2;
