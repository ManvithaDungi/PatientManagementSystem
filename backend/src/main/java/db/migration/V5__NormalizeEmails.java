package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * V5: e-mail addresses become case-insensitive.
 * <ul>
 *   <li>Existing addresses are lower-cased when that cannot collide with another account.</li>
 *   <li>Accounts that differ only by case are REPORTED (WARN log, listed below) and left untouched, never deleted
 *       or merged; an administrator has to resolve them.</li>
 *   <li>On PostgreSQL a unique index on {@code lower(email)} is created when no such duplicates exist.</li>
 * </ul>
 */
public class V5__NormalizeEmails extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V5__NormalizeEmails.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        List<String> duplicates = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select lower(email) as e, count(*) as n from login group by lower(email) having count(*) > 1")) {
            while (rs.next()) {
                duplicates.add(rs.getString("e") + " (" + rs.getInt("n") + " accounts)");
            }
        }

        try (Statement st = c.createStatement()) {
            int lowered = st.executeUpdate(
                    "update login set email = lower(email) where email <> lower(email) and not exists ("
                            + "select 1 from login l2 where lower(l2.email) = lower(login.email) and l2.user_id <> login.user_id)");
            st.executeUpdate("update staff_invites set email = lower(email) where email <> lower(email)");
            log.info("V5: lower-cased {} account e-mail address(es)", lowered);
        }

        if (!duplicates.isEmpty()) {
            log.warn("V5: {} e-mail address(es) are registered more than once with different letter case and were NOT "
                    + "changed. Resolve them manually, then create the index: "
                    + "CREATE UNIQUE INDEX ux_login_email_lower ON login (lower(email)). Affected: {}",
                    duplicates.size(), duplicates);
            return;
        }

        String product = c.getMetaData().getDatabaseProductName();
        if ("PostgreSQL".equalsIgnoreCase(product)) {
            try (Statement st = c.createStatement()) {
                st.execute("create unique index ux_login_email_lower on login (lower(email))");
            }
        } else {
            log.info("V5: skipping the lower(email) unique index on {} (PostgreSQL only)", product);
        }
    }
}
