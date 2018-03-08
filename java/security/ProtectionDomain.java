/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import sun.misc.JavaSecurityProtectionDomainAccess;
import static sun.misc.JavaSecurityProtectionDomainAccess.ProtectionDomainCache;
import sun.misc.SharedSecrets;
import sun.security.util.Debug;
import sun.security.util.SecurityConstants;
import sun.misc.JavaSecurityAccess;
import sun.misc.SharedSecrets;

/**
 *
 *<p>
 * This ProtectionDomain class encapsulates the characteristics of a domain,
 * which encloses a set of classes whose instances are granted a set
 * of permissions when being executed on behalf of a given set of Principals.
 * <p>
 * A static set of permissions can be bound to a ProtectionDomain when it is
 * constructed; such permissions are granted to the domain regardless of the
 * Policy in force. However, to support dynamic security policies, a
 * ProtectionDomain can also be constructed such that it is dynamically
 * mapped to a set of permissions by the current Policy whenever a permission
 * is checked.
 * <p>
 *
 * @author Li Gong
 * @author Roland Schemers
 * @author Gary Ellison
 *
 * 保护域
 * 保护域定义了授予一段特定代码的所有权限。（一个保护域对应策略文件中的一个或多个Grant子句。）
 * 装载入Java虚拟机的每一个类型都属于一个且仅属于一个保护域。
 *
 * 此 ProtectionDomain 类封装域的特征，域中包装一个类集合，在代表给定的主体集合执行这些类的实例时会授予它们一个权限集合。
 *
 * 在构造 ProtectionDomain 时可以对它绑定一个静态的权限集合；不管 Policy 是否有效，都会将这些权限授予域。
 * 但是，为了支持动态安全策略，也可以构造 ProtectionDomain，使得只要检查权限时就能通过当前 Policy 将其动态地映射到一个权限集合。
 */

public class ProtectionDomain {

    static {
        // Set up JavaSecurityAccess in SharedSecrets
        // 在SharedSecrets设置好JavaSecurityAccess
        // 设置java安全访问，这里是使用匿名实现类实现的。
        // 一直在找JavaSecurityAccess的实现类，这里有一个，居然是匿名类难怪找不到。
        SharedSecrets.setJavaSecurityAccess(
            new JavaSecurityAccess() {
                // 执行交叉特权
                public <T> T doIntersectionPrivilege(
                    PrivilegedAction<T> action,
                    final AccessControlContext stack,
                    final AccessControlContext context)
                {
                    if (action == null) {
                        throw new NullPointerException();
                    }
                    return AccessController.doPrivileged(
                        action,
                        new AccessControlContext(
                            stack.getContext(), context).optimize()
                    );
                }

                // 执行交叉特权
                public <T> T doIntersectionPrivilege(
                    PrivilegedAction<T> action,
                    AccessControlContext context)
                {
                    return doIntersectionPrivilege(action,
                        AccessController.getContext(), context);
                }
            }
       );
    }

    /* CodeSource */
    private CodeSource codesource ;

    /* ClassLoader the protection domain was consed from */
    private ClassLoader classloader;

    /* Principals running-as within this protection domain */
    // 保护域中执行的规则
    private Principal[] principals;

    /* the rights this protection domain is granted */
    // 保护域绑定的permissions
    private PermissionCollection permissions;

    /* if the permissions object has AllPermission */
    private boolean hasAllPerm = false;

    /* the PermissionCollection is static (pre 1.4 constructor)
       or dynamic (via a policy refresh) */
    private boolean staticPermissions;

    /*
     * An object used as a key when the ProtectionDomain is stored in a Map.
     * ProtectionDomain放入Map中的可以
     */
    final Key key = new Key();

    // 实现需要到dt.jar中找
    private static final Debug debug = Debug.getInstance("domain");

    /**
     * Creates a new ProtectionDomain with the given CodeSource and
     * Permissions. If the permissions object is not null, then
     *  <code>setReadOnly())</code> will be called on the passed in
     * Permissions object. The only permissions granted to this domain
     * are the ones specified; the current Policy will not be consulted.
     *
     * @param codesource the codesource associated with this domain
     * @param permissions the permissions granted to this domain
     */
    public ProtectionDomain(CodeSource codesource,
                            PermissionCollection permissions) {
        this.codesource = codesource;
        if (permissions != null) {
            this.permissions = permissions;
            this.permissions.setReadOnly();
            if (permissions instanceof Permissions &&
                ((Permissions)permissions).allPermission != null) {
                hasAllPerm = true;
            }
        }
        this.classloader = null;
        this.principals = new Principal[0];
        staticPermissions = true;
    }

    /**
     * Creates a new ProtectionDomain qualified by the given CodeSource,
     * Permissions, ClassLoader and array of Principals. If the
     * permissions object is not null, then <code>setReadOnly()</code>
     * will be called on the passed in Permissions object.
     * The permissions granted to this domain are dynamic; they include
     * both the static permissions passed to this constructor, and any
     * permissions granted to this domain by the current Policy at the
     * time a permission is checked.
     * <p>
     * This constructor is typically used by
     * {@link SecureClassLoader ClassLoaders}
     * and {@link DomainCombiner DomainCombiners} which delegate to
     * <code>Policy</code> to actively associate the permissions granted to
     * this domain. This constructor affords the
     * Policy provider the opportunity to augment the supplied
     * PermissionCollection to reflect policy changes.
     * <p>
     *
     * @param codesource the CodeSource associated with this domain
     * @param permissions the permissions granted to this domain
     * @param classloader the ClassLoader associated with this domain
     * @param principals the array of Principals associated with this
     * domain. The contents of the array are copied to protect against
     * subsequent modification.
     * @see Policy#refresh
     * @see Policy#getPermissions(ProtectionDomain)
     * @since 1.4
     */
    public ProtectionDomain(CodeSource codesource,
                            PermissionCollection permissions,
                            ClassLoader classloader,
                            Principal[] principals) {
        this.codesource = codesource;
        if (permissions != null) {
            this.permissions = permissions;
            this.permissions.setReadOnly();
            if (permissions instanceof Permissions &&
                ((Permissions)permissions).allPermission != null) {
                hasAllPerm = true;
            }
        }
        this.classloader = classloader;
        this.principals = (principals != null ? principals.clone():
                           new Principal[0]);
        staticPermissions = false;
    }

    /**
     * Returns the CodeSource of this domain.
     * @return the CodeSource of this domain which may be null.
     * @since 1.2
     */
    public final CodeSource getCodeSource() {
        return this.codesource;
    }


    /**
     * Returns the ClassLoader of this domain.
     * @return the ClassLoader of this domain which may be null.
     *
     * @since 1.4
     */
    public final ClassLoader getClassLoader() {
        return this.classloader;
    }


    /**
     * Returns an array of principals for this domain.
     * @return a non-null array of principals for this domain.
     * Returns a new array each time this method is called.
     *
     * @since 1.4
     */
    public final Principal[] getPrincipals() {
        return this.principals.clone();
    }

    /**
     * Returns the static permissions granted to this domain.
     *
     * @return the static set of permissions for this domain which may be null.
     * @see Policy#refresh
     * @see Policy#getPermissions(ProtectionDomain)
     */
    public final PermissionCollection getPermissions() {
        return permissions;
    }

    /**
     * Check and see if this ProtectionDomain implies the permissions
     * expressed in the Permission object.
     *
     * 检查并查看此ProtectionDomain是否暗含了Permission对象表示的权限。
     *
     * <p>
     * The set of permissions evaluated is a function of whether the
     * ProtectionDomain was constructed with a static set of permissions
     * or it was bound to a dynamically mapped set of permissions.
     * <p>
     * If the ProtectionDomain was constructed to a
     * {@link #ProtectionDomain(CodeSource, PermissionCollection)
     * statically bound} PermissionCollection then the permission will
     * only be checked against the PermissionCollection supplied at
     * construction.
     * <p>
     * However, if the ProtectionDomain was constructed with
     * the constructor variant which supports
     * {@link #ProtectionDomain(CodeSource, PermissionCollection,
     * ClassLoader, java.security.Principal[]) dynamically binding}
     * permissions, then the permission will be checked against the
     * combination of the PermissionCollection supplied at construction and
     * the current Policy binding.
     * <p>
     *
     * @param permission the Permission object to check.
     *
     * @return true if "permission" is implicit to this ProtectionDomain.
     */
    public boolean implies(Permission permission) {

        if (hasAllPerm) {
            // internal permission collection already has AllPermission -
            // no need to go to policy
            return true;
        }

        // 如果是动态的权限验证，加载。这里正好可以看看Policy是如何加载的（这里最好先看PolicyFile类的实现）
        if (!staticPermissions &&
            Policy.getPolicyNoCheck().implies(this, permission))
            return true;
        if (permissions != null)
            return permissions.implies(permission);

        return false;
    }

    /**
     * Convert a ProtectionDomain to a String.
     */
    @Override public String toString() {
        String pals = "<no principals>";
        if (principals != null && principals.length > 0) {
            StringBuilder palBuf = new StringBuilder("(principals ");

            for (int i = 0; i < principals.length; i++) {
                palBuf.append(principals[i].getClass().getName() +
                            " \"" + principals[i].getName() +
                            "\"");
                if (i < principals.length-1)
                    palBuf.append(",\n");
                else
                    palBuf.append(")\n");
            }
            pals = palBuf.toString();
        }

        // Check if policy is set; we don't want to load
        // the policy prematurely here
        PermissionCollection pc = Policy.isSet() && seeAllp() ?
                                      mergePermissions():
                                      getPermissions();

        return "ProtectionDomain "+
            " "+codesource+"\n"+
            " "+classloader+"\n"+
            " "+pals+"\n"+
            " "+pc+"\n";
    }

    /**
     * Return true (merge policy permissions) in the following cases:
     *
     * . SecurityManager is null
     *
     * . SecurityManager is not null,
     *          debug is not null,
     *          SecurityManager impelmentation is in bootclasspath,
     *          Policy implementation is in bootclasspath
     *          (the bootclasspath restrictions avoid recursion)
     *
     * . SecurityManager is not null,
     *          debug is null,
     *          caller has Policy.getPolicy permission
     */
    private static boolean seeAllp() {
        SecurityManager sm = System.getSecurityManager();

        if (sm == null) {
            return true;
        } else {
            if (debug != null) {
                if (sm.getClass().getClassLoader() == null &&
                    Policy.getPolicyNoCheck().getClass().getClassLoader()
                                                                == null) {
                    return true;
                }
            } else {
                try {
                    sm.checkPermission(SecurityConstants.GET_POLICY_PERMISSION);
                    return true;
                } catch (SecurityException se) {
                    // fall thru and return false
                }
            }
        }

        return false;
    }

    private PermissionCollection mergePermissions() {
        if (staticPermissions)
            return permissions;

        PermissionCollection perms =
            java.security.AccessController.doPrivileged
            (new java.security.PrivilegedAction<PermissionCollection>() {
                    public PermissionCollection run() {
                        Policy p = Policy.getPolicyNoCheck();
                        return p.getPermissions(ProtectionDomain.this);
                    }
                });

        Permissions mergedPerms = new Permissions();
        int swag = 32;
        int vcap = 8;
        Enumeration<Permission> e;
        List<Permission> pdVector = new ArrayList<>(vcap);
        List<Permission> plVector = new ArrayList<>(swag);

        //
        // Build a vector of domain permissions for subsequent merge
        if (permissions != null) {
            synchronized (permissions) {
                e = permissions.elements();
                while (e.hasMoreElements()) {
                    pdVector.add(e.nextElement());
                }
            }
        }

        //
        // Build a vector of Policy permissions for subsequent merge
        if (perms != null) {
            synchronized (perms) {
                e = perms.elements();
                while (e.hasMoreElements()) {
                    plVector.add(e.nextElement());
                    vcap++;
                }
            }
        }

        if (perms != null && permissions != null) {
            //
            // Weed out the duplicates from the policy. Unless a refresh
            // has occured since the pd was consed this should result in
            // an empty vector.
            synchronized (permissions) {
                e = permissions.elements();   // domain vs policy
                while (e.hasMoreElements()) {
                    Permission pdp = e.nextElement();
                    Class pdpClass = pdp.getClass();
                    String pdpActions = pdp.getActions();
                    String pdpName = pdp.getName();
                    for (int i = 0; i < plVector.size(); i++) {
                        Permission pp = plVector.get(i);
                        if (pdpClass.isInstance(pp)) {
                            // The equals() method on some permissions
                            // have some side effects so this manual
                            // comparison is sufficient.
                            if (pdpName.equals(pp.getName()) &&
                                pdpActions.equals(pp.getActions())) {
                                plVector.remove(i);
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (perms !=null) {
            // the order of adding to merged perms and permissions
            // needs to preserve the bugfix 4301064

            for (int i = plVector.size()-1; i >= 0; i--) {
                mergedPerms.add(plVector.get(i));
            }
        }
        if (permissions != null) {
            for (int i = pdVector.size()-1; i >= 0; i--) {
                mergedPerms.add(pdVector.get(i));
            }
        }

        return mergedPerms;
    }

    /**
     * Used for storing ProtectionDomains as keys in a Map.
     */
    final class Key {}

    static {
        SharedSecrets.setJavaSecurityProtectionDomainAccess(
                // 设置默认的JavaSecurityProtectionDomainAccess
            new JavaSecurityProtectionDomainAccess() {
                public ProtectionDomainCache getProtectionDomainCache() {
                    return new ProtectionDomainCache() {
                        private final Map<Key, PermissionCollection> map =
                            Collections.synchronizedMap
                                (new WeakHashMap<Key, PermissionCollection>());
                        public void put(ProtectionDomain pd,
                            PermissionCollection pc) {
                            map.put((pd == null ? null : pd.key), pc);
                        }
                        public PermissionCollection get(ProtectionDomain pd) {
                            return pd == null ? map.get(null) : map.get(pd.key);
                        }
                    };
                }
            });
    }
}
