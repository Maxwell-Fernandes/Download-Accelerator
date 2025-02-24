# Download Accelerator

A multi-threaded download accelerator written in Java that speeds up file downloads by splitting them into multiple chunks and downloading them concurrently. Supports pausing, resuming, and graceful shutdown.

## Table of Contents

*   [Features](#features)
*   [Prerequisites](#prerequisites)
*   [Installation](#installation)
*   [Usage](#usage)
*   [How it Works](#how-it-works)
*   [Configuration](#configuration)
*   [Error Handling and Logging](#error-handling-and-logging)
*   [Security](#security)
*   [Demonstration](#Demonstration)
*   [Contributing](#contributing)
*   [Acknowledgements](#acknowledgements)

## Features

*   **Multi-threaded Downloading:**  Divides the download into multiple chunks, downloading them concurrently for faster speeds.  The number of threads is configurable.
*   **Pause and Resume:**  Allows pausing and resuming downloads, preserving progress even if the application is interrupted.
*   **Graceful Shutdown:**  Handles program termination gracefully, saving the download state so it can be resumed later.
*   **Metadata Persistence:** Stores download metadata (URL, filename, progress) securely to disk using encryption for resume functionality.
*   **Error Handling:**  Implements retry mechanisms for failed chunk downloads and robust error logging.
*   **Command-Line Interface:** Simple command-line interface for starting, pausing, resuming, and exiting downloads.
*   **Logging:**  Detailed logging using `java.util.logging` to track download progress and potential issues.
*   **Secure Metadata Storage:** Employs AES encryption to protect the metadata file, enhancing security.
*   **Backup Mechanism:** Implements a backup mechanism when saving metadata to prevent data loss in case of failures.

## Prerequisites

*   **Java Development Kit (JDK):**  Version 8 or higher.  Make sure you have a JDK installed and configured on your system.  You can download it from [Oracle](https://www.oracle.com/java/technologies/javase-downloads.html) or use an open-source distribution like [OpenJDK](https://openjdk.java.net/).
*   **Internet Connection:** A stable internet connection is required to download files.

## Installation

1.  **Clone the Repository:**

    ```bash
    git clone https://github.com/Maxwell-Fernandes/Download-Accelerator
    cd download-accelerator
    ```

2.  **Compile the Code:**

    ```bash
    javac DownloadAccelerator.java ChunkDownloader.java Downloader.java Metadata.java MetadataStore.java
    ```

## Usage

1.  **Run the Application:**

    ```bash
    java DownloadAccelerator
    ```

2.  **Follow the Prompts:** The application will prompt you to enter:

    *   **Download URL:** The URL of the file you want to download.
    *   **Filename to Save As:** The name you want to give the downloaded file on your local system.

3.  **Control the Download:** During the download, you can enter the following commands:

    *   `pause`:  Pauses the download.
    *   `resume`: Resumes the paused download.
    *   `exit`: Stops the download and exits the application. The download state will be saved, allowing you to resume later.

## How it Works

The `DownloadAccelerator` class orchestrates the download process. Here's a breakdown:

1.  **Input:** Prompts the user for the download URL and filename.
2.  **Metadata Handling:**
    *   Checks for existing metadata for the given filename.  If metadata exists, it resumes the download from the last saved progress.
    *   If no metadata exists, it creates new metadata.
3.  **File Size Determination:**  Retrieves the file size from the server using an HTTP HEAD request. If the file size cannot be determined, it defaults to a single-threaded download.
4.  **Chunking:** Divides the file into multiple chunks based on the `NUM_THREADS` constant.
5.  **Thread Pool:** Creates a fixed-size thread pool using `ExecutorService` to manage the download threads.
6.  **Chunk Downloaders:**  Creates `ChunkDownloader` instances, each responsible for downloading a specific chunk of the file.  These are submitted to the thread pool.
7.  **Progress Monitoring:**  A separate thread monitors the download progress and listens for user commands (pause, resume, exit).
8.  **Pause/Resume Logic:**  The `paused` flag is used to pause and resume downloads.  `ChunkDownloader` checks this flag periodically and yields the thread if paused.
9.  **Error Handling:** `ChunkDownloader` retries failed chunk downloads up to `MAX_RETRIES` times.
10. **Metadata Updates:**  The `downloadedBytes` counter is updated atomically using `AtomicLong` to ensure thread safety.  Metadata is periodically saved to disk.
11. **Completion:** Once all chunks are downloaded, the metadata is deleted, and a success message is displayed. If any chunk fails after multiple retries, an error message is shown.

The `ChunkDownloader` class handles the actual downloading of individual chunks. It uses `HttpURLConnection` to make ranged requests, specifying the start and end bytes for each chunk.

The `MetadataStore` class handles the saving and loading of download metadata to disk. It serializes the `Metadata` object and stores it in a file with the `.dwnld` extension. Encryption is used to secure the metadata.

## Configuration

*   **`NUM_THREADS`:**  The number of threads to use for downloading.  This value is defined as a constant in the `DownloadAccelerator` class.  Experiment with different values to find the optimal setting for your network and system.  A higher number of threads may not always result in faster downloads.
*   **`BUFFER_SIZE`:** The size of the buffer used for reading and writing data. This is defined in the `ChunkDownloader` class. Adjusting the buffer size can sometimes improve performance.
*   **`MAX_RETRIES`:** The maximum number of times a chunk download will be retried. Defined in `ChunkDownloader`.
*   **`CHECK_INTERVAL_MS`:** The interval (in milliseconds) at which the download progress is checked and displayed. Defined in `DownloadAccelerator`.
*   **`KEY_FILE`:** The name of the file used to store the AES encryption key. Stored in `MetadataStore`.
*   **`ALGORITHM`:** The encryption algorithm used to encrypt the metadata file. Stored in `MetadataStore`

These settings can be modified directly in the source code before compiling.

## Error Handling and Logging

The application uses `java.util.logging` for logging.  Log messages are written to the console and can be configured to be written to a file as well.  The logging level can be adjusted to control the verbosity of the logs.

*   **`Level.INFO`:**  Provides general information about the download process.
*   **`Level.WARNING`:**  Indicates potential issues or errors that do not necessarily cause the download to fail.
*   **`Level.SEVERE`:**  Indicates critical errors that prevent the download from completing.

Check the console output for error messages and warnings.  Examine the log files for more detailed information about any problems that occur.

## Security

*   **Metadata Encryption:** The download metadata, which includes the URL, filename, and download progress, is encrypted using AES encryption before being stored on disk.  This protects sensitive information from unauthorized access.
*   **Key Management:** The AES encryption key is stored in a separate file (`metadata.key`).  It is important to protect this key from unauthorized access. Consider restricting file system permissions on this file.
*   **Input Validation:** While not explicitly implemented in the provided code, it is recommended to add input validation to prevent malicious URLs or filenames from being used.
*   **HTTPS:**  Always use HTTPS URLs whenever possible to ensure that the downloaded data is encrypted in transit.
*   **Backup Mechanism:** Implemented a backup mechanism when saving metadata to prevent data loss in case of failures.
*   **Key Generation:** If the key file does not exist, a new AES key is generated using a `SecureRandom` instance.

## Demonstration

Here's a short video demonstrating the key features of the Download Accelerator:

![Demonstration of Download Accelerator](demoo.gif)

## Contributing

Contributions are welcome!  Please feel free to submit pull requests with bug fixes, new features, or improvements to the code.

1.  Fork the repository.
2.  Create a new branch for your feature or bug fix.
3.  Make your changes.
4.  Commit your changes with descriptive commit messages.
5.  Push your changes to your forked repository.
6.  Submit a pull request.

## Acknowledgements

*   This project was inspired by the need for a simple and reliable download accelerator.
*   Aria2
