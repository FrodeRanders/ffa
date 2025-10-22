package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.*;

public final class MutationSemantics {
    private static final Logger log = LoggerFactory.getLogger(MutationSemantics.class);

    public static final class BeanState {
        public byte[] digest;

        public boolean compareWith(byte[] current) {
            return MessageDigest.isEqual(current, digest);
        }

        public void resetTo(byte[] current) {
            digest = current;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("BeanState{");
            sb.append("digest=").append(DigestUtils.hexGrouped(digest, 4, " ", false));
            sb.append('}');
            return sb.toString();
        }
    }

    // Identity-based weak key
    private static final class IdentityKey extends WeakReference<Object> {
        private final String className; // initially kept for debugging purposes
        private final int identityHash;

        IdentityKey(Object referent) {
            super(referent);
            this.identityHash = System.identityHashCode(referent);
            this.className = referent.getClass().getName();
        }

        @Override public int hashCode() {
            return identityHash;
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof IdentityKey other)) return false;
            Object a = this.get(), b = other.get();
            return a == b && a != null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(className);
            sb.append("@").append(String.format("%08x", identityHash));
            return sb.toString();
        }
    }

    // Weak keys together with identity semantics
    private static final Map<IdentityKey, BeanState> STATE = Collections.synchronizedMap(new WeakHashMap<>());

    public static BeanState of(Object o) {
        log.trace("Initiating state for bean: {}@{}", o.getClass().getCanonicalName(), String.format("%08x", o.hashCode()));
        return STATE.computeIfAbsent(new IdentityKey(o), k -> new BeanState());
    }

    public static void dump() {
        StringBuilder sb = new StringBuilder("\nBean states:\n");
        for (Map.Entry<IdentityKey, BeanState> entry : STATE.entrySet()) {
            sb.append("   ").append(entry.getKey()).append(" -> "). append(entry.getValue()).append("\n");
        }
        log.debug(sb.toString());
    }
}