import java.io.FileDescriptor
import java.net.SocketAddress

/**
 * A SocketAddress representing a FileDescriptor.
 * This allows treating a FileDescriptor as a channel endpoint in Netty.
 */
class FildesAddress(val fd: FileDescriptor) : SocketAddress() {

    // It's good practice to ensure fd is usable/valid if possible,
    // though SocketAddress itself doesn't mandate this.
    val isValid: Boolean = fd.valid() // Example check

    override fun toString(): String {
        return "FildesAddress(fd=${System.identityHashCode(fd)})" // Or a more descriptive ID if available
    }

    // Implement equals() and hashCode() if these addresses will be used in collections or for comparison.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FildesAddress) return false
        return fd == other.fd // Be careful with direct fd comparison if they can be recreated
    }

    override fun hashCode(): Int {
        return fd.hashCode()
    }
}

