# TalentLens UI Screens by Role

This directory contains full-resolution UI screenshots of the **TalentLens Enterprise Resume Screening Platform** captured directly from the live system in **Microsoft Edge**.

Screenshots are organized strictly by **system roles** and key platform workflows with zero personal identifiers.

---

## 🧭 Directory Overview

```text
docs/design/screens/
├── 00_Login_Screen.png                         # Sign-in portal
├── 01_Register_Screen.png                      # Account registration portal
├── Admin/                                      # Administrator (Full system privileges)
│   ├── Landing_Page.png                        # Initial dashboard landing
│   ├── Sessions_List.png                       # Screening sessions overview
│   ├── Session_Detail_Standings.png            # Ranked candidate standings & scores
│   ├── Candidate_Detail_View.png               # Deep-dive candidate evaluation & radar chart
│   ├── New_Session_Wizard.png                  # Multi-weight scoring & JD/resume upload
│   ├── Hiring_Pipeline.png                     # Multi-stage hiring pipeline board
│   ├── My_Interviews.png                       # Assigned interview panels
│   ├── Recruiting_Analytics.png                # Throughput, velocity & funnel analytics
│   ├── Admin_Users_Management.png              # System user roster & role assignment
│   └── Admin_Roles_and_Permissions.png         # Dynamic role-to-permission matrix
├── HR_Chief/                                   # HR Chief (Hiring oversight & analytics)
│   ├── Landing_Page.png                        # HR Chief landing view
│   ├── Sessions_List.png                       # Organization-wide sessions
│   ├── Session_Detail.png                      # Candidate review & shortlist cut-offs
│   ├── Hiring_Pipeline.png                     # Pipeline stage progression
│   └── Recruiting_Analytics.png                # Departmental recruiting analytics
├── Recruiter/                                  # Recruiter (Intake, scoring & pipeline management)
│   ├── Landing_Page.png                        # Recruiter dashboard
│   ├── Sessions_List.png                       # Active and completed sessions
│   ├── New_Session_Wizard.png                  # Session configuration with criteria sliders
│   ├── Session_Detail_Standings.png            # Candidate ranking table & review
│   ├── Candidate_Detail_View.png               # Candidate profile, notes & interview scheduling
│   └── Hiring_Pipeline.png                     # Sourced-to-Hired pipeline management
├── HR_Interviewer/                             # HR Interviewer (Behavioral & HR evaluation)
│   ├── Landing_Page.png                        # Interviewer portal landing
│   └── My_Interviews.png                       # Assigned interviews & candidate feedback
├── Technical_Interviewer/                      # Technical Interviewer (Code & domain evaluation)
│   ├── Landing_Page.png                        # Interviewer portal landing
│   └── My_Interviews.png                       # Assigned interviews & candidate feedback
├── Department_Director/                        # Department Director (Stage review & offer approvals)
│   ├── Landing_Page.png                        # Direct pipeline landing
│   ├── Hiring_Pipeline.png                     # Team candidate pipeline
│   └── My_Interviews.png                       # Interview panel participation
├── Chairman/                                   # Chairman (Executive oversight)
│   ├── Landing_Page.png                        # Executive portal
│   ├── My_Interviews.png                       # Executive interviews
│   └── Recruiting_Analytics.png                # High-level talent acquisition metrics
└── CEC_CEO/                                    # CEO / CEC (Executive oversight & offer authorization)
    ├── Landing_Page.png                        # Executive portal
    ├── My_Interviews.png                       # Executive interviews
    └── Recruiting_Analytics.png                # Executive reporting & analytics
```

---

## 🎯 Role-Based Capabilities Matrix

| System Role | Sessions & Scoring | Shortlist Review | Hiring Pipeline | Interview Feedback | Offer Approval | Recruiting Analytics | User & Role Admin |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Admin** | ✅ (Full) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **HR Chief** | ✅ (All Sessions) | ✅ | ✅ | — | ✅ | ✅ | — |
| **Recruiter** | ✅ (Own Sessions) | ✅ | ✅ | — | — | — | — |
| **HR Interviewer** | — | — | — | ✅ | — | — | — |
| **Technical Interviewer** | — | — | — | ✅ | — | — | — |
| **Department Director** | — | — | ✅ | ✅ | ✅ | — | — |
| **Chairman** | — | — | — | ✅ | ✅ | ✅ | — |
| **CEC / CEO** | — | — | — | ✅ | ✅ | ✅ | — |

---

## 💡 Key Screen Highlights

1. **Roles and Permissions Matrix (`Admin/Admin_Roles_and_Permissions.png`)**
   - Dynamic checkbox grid allowing administrators to configure granular permissions per role without code modifications.

2. **Hiring Pipeline Kanban (`Admin/Hiring_Pipeline.png`)**
   - Candidate stage flow: *Sourced* ➔ *Screening* ➔ *Interviewing* ➔ *Interviewed* ➔ *Offer* ➔ *Joining*.
   - Drag-and-drop or status transitions with automated stage timestamp logging.

3. **Interview Planning & Places (`Candidate_Detail_View.png`)**
   - Multi-mode interview logistics: Onsite room booking, online video conference links, and phone screen details.

4. **Candidate Detail & AI Radar (`Candidate_Detail_View.png`)**
   - 5-axis criteria radar chart, explainable strengths and gaps breakdown, candidate notes, and review status.

5. **Recruiting Analytics (`Recruiting_Analytics.png`)**
   - 30 / 90 / 365-day views of throughput, candidate quality distributions, and recruiter activity.
