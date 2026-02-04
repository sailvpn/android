import socket
import time

# setup forward to android emulator
# adb forward tcp:6789 tcp:6789

# stop adb forward
# adb forward --remove tcp:6789

# remove all forward setting
# adb forward --remove-all

# list adb forward
# adb forward --list

# The host is always your computer's loopback address.
HOST = "127.0.0.1"
# The port must match the host port you specified in the 'adb forward' command.
PORT = 6789

# The message you want to send.
# For your VPN, this should eventually be a valid IP packet as bytes.
# For now, we'll just send a simple string.
message = b"Hello from TCP client!"

try:
    # Create a TCP socket
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        # Connect to the server (which is the adb tunnel)
        print(f"Connecting to {HOST}:{PORT}...")
        sock.connect((HOST, PORT))

        # Send the message
        print(f"Sending message: {message}")
        sock.sendall(message)

        # Keep the connection open for a moment to allow the server to process
        time.sleep(2)

        print("Closing socket.")

except ConnectionRefusedError:
    print(f"Connection refused. Is the adb forward rule set up and is an app listening in the emulator?")
except Exception as e:
    print(f"An error occurred: {e}")