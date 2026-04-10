# CapstoneDesign
개인정보 보호형 LLM 기반 실시간 근로계약서 분석 및 가이드 시스템


# 🛠 **Git Workflow Guide (Visual C++ in Visual Studio)**

This workflow explains how to manage issues, branches, commits, and pull requests when developing **C++ projects in Visual Studio** using GitHub.

---

## ✅ **1. Create an Issue on GitHub**
*   Go to your repository → **Issues**.
*   Click **New Issue**, write the title & description.
*   Note the **issue number** (example: `#22`).

---

## ✅ **2. Create a Branch on GitHub**
*   Go to the **Code** tab.
*   Make sure the base branch is **main**.
*   Create a new branch named:

```
issue-<issue-number>
```

**Example:**  
```
issue-22
```

---

### ✔ Up to this point, all work is done in the GitHub web browser

---

# 🖥 **Now Move to Visual Studio (Visual C++)**

Visual Studio has built‑in Git tools, but you can also use the terminal.  
Below is the workflow using the **terminal**, which is more reliable and consistent.

---

## ✅ **3. Open Visual Studio and Check Your Current Branch**

In Visual Studio:

*   Open your solution (`.sln`)
*   Open **View → Terminal** or **Developer PowerShell**

Then run:

```bash
git status
```

---

## ✅ **4. Switch to the Base Branch (main)**

```bash
git checkout main
```

---

## ✅ **5. Fetch the Latest Branch Information**

```bash
git fetch
```

---

## ✅ **6. Pull the Latest Changes**

```bash
git pull
```

---

## ✅ **7. Switch to Your New Branch**

```bash
git checkout issue-<issue-number>
```

---

## ✅ **8. Confirm You’re on the Correct Branch**

```bash
git status
```

---

### ✔ Up to this point: all steps are performed in the Visual Studio terminal

---

# 🧑‍💻 **9. Do Your C++ Development Work**
Inside Visual Studio:

*   Edit `.cpp`, `.h`, `.vcxproj` files
*   Build the project
*   Run and debug
*   Test your changes

---

## ✅ **10. Check Branch Before Committing**

```bash
git status
```

---

## ✅ **11. Stage Your Changes**

```bash
git add .
```

---

## ✅ **12. Commit Your Work**

Commit message format:

```
<label>: <description> #<issue-number>
```

**Examples:**

```bash
git commit -m "fix: resolve buffer overflow in parser #22"
git commit -m "feat: add logging module #22"
```

---

## ✅ **13. Push Your Branch to GitHub**

```bash
git push
```

---

### ✔ This is also done in the Visual Studio terminal

---

# 🔀 **14. Create a Pull Request**
On GitHub:

*   Go to **Pull Requests**
*   Click **New Pull Request**
*   Make sure the **base branch** is `main`
*   Submit the PR for review

---

# 🎯 **Tips for Visual Studio + Git**
*   Visual Studio’s Git UI is convenient, but the **terminal is more predictable**.
*   Always update `main` before starting new work.
*   Keep branches small and focused on one issue.
*   Delete your branch after merging to keep the repo clean.

---
