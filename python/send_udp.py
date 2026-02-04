import socket

# setup forward to android emulator
# adb forward udp:8888 udp:8888

# stop adb forward
# adb forward --remove udp:8888

# remove all forward setting
# adb forward --remove-all

# list adb forward
# adb forward --list

# The host is your own computer.
HOST = "127.0.0.1"
# The port must match the one you forwarded with adb.
PORT = 8888

# The message you want to send.
# It needs to be a valid IP packet in byte format for your app to process it.
# This is just a placeholder example.
message = b"This is a test packet"

try:
    # Create a UDP socket
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        # Send the message
        sock.sendto(message, (HOST, PORT))
        print(f"UDP packet sent to {HOST}:{PORT}")

except Exception as e:
    print(f"Error sending packet: {e}")