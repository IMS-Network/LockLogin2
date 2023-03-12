package es.karmadev.locklogin.api.security.backup.store;

import es.karmadev.locklogin.api.security.hash.HashResult;
import es.karmadev.locklogin.api.user.account.migration.Transictionable;
import ml.karmaconfigs.api.common.string.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * User backup data
 */
public interface UserBackup extends Transictionable {

    /**
     * Get the transictionable account id
     *
     * @return the account id
     */
    int account();

    /**
     * Get the user name
     *
     * @return the user name
     */
    String name();

    /**
     * Get the user unique id
     *
     * @return the user uid
     */
    UUID uniqueId();

    /**
     * Get if the user had persistent
     * sessions
     *
     * @return if the user had persistent
     * sessions
     */
    boolean sessionPersistent();

    /**
     * Get when the account was created
     *
     * @return the account creation date
     */
    Instant creation();
}
