/**
 * =========================================================================================
 * 100% Free Google Apps Script Webhook for WhatsApp Document Chatbot
 * Cloud Storage Account: hrudaypreetham10@gmail.com
 * Admin Account: hrudaypreetham10@gmail.com
 * Zero Cost | Zero Google Cloud Console Setup | Zero Credit Card Required
 * =========================================================================================
 *
 * HOW TO DEPLOY (Takes 2 minutes):
 * 1. Log into your browser with: hrudaypreetham10@gmail.com
 * 2. Go to https://script.google.com and click "+ New project".
 * 3. Delete any existing code, paste this ENTIRE file into the editor, and click "Save" (Ctrl+S).
 * 4. Click "Deploy" > "New deployment" (top right).
 * 5. Select type: "Web app" (click the gear icon next to "Select type").
 *    - Description: "WhatsApp Document Vault Sync"
 *    - Execute as: "Me (hrudaypreetham10@gmail.com)"
 *    - Who has access: "Anyone"
 * 6. Click "Deploy" and authorize access when prompted (will authorize cleanly without Advanced Protection block).
 * 7. Copy the "Web app URL" (looks like: https://script.google.com/macros/s/.../exec).
 * 8. Paste that URL into application.yml:
 *    storage:
 *      apps-script-webhook-url: "https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec"
 *
 * That's it! Every document uploaded by any family member will automatically be organized into:
 * Google Drive (hrudaypreetham10@gmail.com) -> chatbot_documents -> {UserName} -> file.pdf
 * =========================================================================================
 */

// Shared secret token configured in application.yml
var SECRET_TOKEN = "family-vault-gas-secret-2026";

// Admin email for the vault
var ADMIN_EMAIL = "hrudaypreetham10@gmail.com";

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return responseJson({ status: "ERROR", message: "No post data received" });
    }

    var payload = JSON.parse(e.postData.contents);

    // Verify secret authentication token
    if (SECRET_TOKEN && payload.secret !== SECRET_TOKEN) {
      return responseJson({ status: "UNAUTHORIZED", message: "Invalid secret token" });
    }

    var userName = payload.userName ? payload.userName.replace(/[^a-zA-Z0-9_-]/g, "_") : "general";
    var fileName = payload.fileName || ("document_" + new Date().getTime());
    var mimeType = payload.mimeType || "application/octet-stream";
    var fileBase64 = payload.fileBase64;

    // 1. Check or create root folder: "chatbot_documents"
    var rootFolders = DriveApp.getFoldersByName("chatbot_documents");
    var rootFolder;
    if (rootFolders.hasNext()) {
      rootFolder = rootFolders.next();
    } else {
      rootFolder = DriveApp.createFolder("chatbot_documents");
    }

    // Automatically share the root folder with Master Admin email if not already shared
    if (ADMIN_EMAIL) {
      try {
        var editors = rootFolder.getEditors();
        var alreadyEditor = false;
        for (var i = 0; i < editors.length; i++) {
          if (editors[i].getEmail().toLowerCase() === ADMIN_EMAIL.toLowerCase()) {
            alreadyEditor = true;
            break;
          }
        }
        if (!alreadyEditor) {
          rootFolder.addEditor(ADMIN_EMAIL);
        }
      } catch (shareErr) {
        // Non-blocking: continue saving file even if sharing notification fails
      }
    }

    // 2. Check or create user-specific subfolder: "chatbot_documents/{userName}"
    var userFolders = rootFolder.getFoldersByName(userName);
    var userFolder;
    if (userFolders.hasNext()) {
      userFolder = userFolders.next();
    } else {
      userFolder = rootFolder.createFolder(userName);
    }

    // If folder creation request without file upload, return folder creation success immediately
    if (!fileBase64 || payload.action === "CREATE_FOLDER") {
      return responseJson({
        status: "SUCCESS",
        action: "FOLDER_CREATED",
        folder: "chatbot_documents/" + userName,
        message: "Folder 'chatbot_documents/" + userName + "' ready in Google Drive"
      });
    }

    // 3. Decode Base64 and write the file into user folder
    var decodedBytes = Utilities.base64Decode(fileBase64);
    var blob = Utilities.newBlob(decodedBytes, mimeType, fileName);
    var savedFile = userFolder.createFile(blob);

    return responseJson({
      status: "SUCCESS",
      fileId: savedFile.getId(),
      fileName: savedFile.getName(),
      folder: "chatbot_documents/" + userName,
      url: savedFile.getUrl()
    });

  } catch (err) {
    return responseJson({ status: "ERROR", message: err.toString() });
  }
}

function responseJson(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

function doGet(e) {
  return ContentService.createTextOutput(
    "✅ WhatsApp Family Vault Google Apps Script is ACTIVE for Admin & Drive owner: hrudaypreetham10@gmail.com."
  );
}
