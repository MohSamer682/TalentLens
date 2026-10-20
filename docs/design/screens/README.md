# Screens by role

Screenshots of the running app, one folder per role. Each folder shows what that role sees with the default permissions. No real personal data appears in them.

```text
docs/design/screens/
├── 00_Login_Screen.png                  Sign in
├── 01_Register_Screen.png               Sign up
├── Admin/
│   ├── Landing_Page.png
│   ├── Sessions_List.png
│   ├── Session_Detail_Standings.png     Ranked standings with scores
│   ├── Candidate_Detail_View.png        Score breakdown, strengths, gaps and pipeline
│   ├── New_Session_Wizard.png           Weights, job description and resume upload
│   ├── Hiring_Pipeline.png
│   ├── My_Interviews.png
│   ├── Recruiting_Analytics.png
│   ├── Admin_Users_Management.png
│   └── Admin_Roles_and_Permissions.png
├── HR_Chief/
│   ├── Landing_Page.png
│   ├── Sessions_List.png                Every recruiter's sessions
│   ├── Session_Detail.png               View-only session
│   ├── Hiring_Pipeline.png
│   └── Recruiting_Analytics.png
├── Recruiter/
│   ├── Landing_Page.png
│   ├── Sessions_List.png
│   ├── New_Session_Wizard.png
│   ├── Session_Detail_Standings.png
│   ├── Candidate_Detail_View.png        Notes and interview scheduling
│   └── Hiring_Pipeline.png
├── HR_Interviewer/
│   ├── Landing_Page.png
│   └── My_Interviews.png
├── Technical_Interviewer/
│   ├── Landing_Page.png
│   └── My_Interviews.png
├── Department_Director/
│   ├── Landing_Page.png
│   ├── Hiring_Pipeline.png
│   └── My_Interviews.png
├── Chairman/
│   ├── Landing_Page.png
│   ├── My_Interviews.png
│   └── Recruiting_Analytics.png
└── CEC_CEO/
    ├── Landing_Page.png
    ├── My_Interviews.png
    └── Recruiting_Analytics.png
```

The candidate portal, profile pages, staff sign-in at `/admins` and the per-role pages under Roles were added after these were taken and aren't shown yet.

## What each role can do by default

Admins can change these on the roles page. The table shows a fresh install.

| Role | Run sessions | Open any session | Shortlist | Move pipeline | Interview feedback | Approve offers | Analytics | Users and roles |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Admin | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| HR chief | | Yes | Yes | | | Yes | Yes | |
| Recruiter | Yes | | Yes | Own | | | | |
| HR interviewer | | | | | Yes | | | |
| Technical interviewer | | | | | Yes | | | |
| Department director | | | | | Yes | Yes | | |
| CEO, Chairman | | | | | Yes | | | |

"Own" means only in sessions the recruiter started. The HR chief opens sessions read-only.

## Screens worth a closer look

- **Roles and permissions** (`Admin/Admin_Roles_and_Permissions.png`): admins switch permissions per role without a code change.
- **Hiring pipeline** (`Admin/Hiring_Pipeline.png`): Shortlisted, Contacted, Interviewing, Interviewed, Offer, Offer approved, Offer sent, Accepted or Declined, Joining. Filter by stage or by offers waiting for approval. Every stage change records who made it and when.
- **Candidate detail** (`Recruiter/Candidate_Detail_View.png`): the five criterion scores, strengths and gaps, team notes, the interview plan with place (HQ, online or other), and the candidate's stage.
- **Recruiting analytics** (`Admin/Recruiting_Analytics.png`): 30 days, 90 days or 12 months of throughput, funnel, stage timing, score calibration and recruiter and interviewer tables.
