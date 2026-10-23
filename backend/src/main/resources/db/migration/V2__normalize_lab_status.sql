-- Lab order statuses now use one vocabulary: Pending, Collected, Results Pending, Completed.
update lab_tests set status = 'Pending'         where upper(status) = 'PENDING' or status is null;
update lab_tests set status = 'Collected'       where upper(status) = 'COLLECTED';
update lab_tests set status = 'Results Pending' where upper(replace(status, '_', ' ')) = 'RESULTS PENDING';
update lab_tests set status = 'Completed'       where upper(status) = 'COMPLETED';
