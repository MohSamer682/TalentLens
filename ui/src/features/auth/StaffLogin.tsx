import { Link } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';
import { SignInForm } from './SignInForm';

export default function StaffLogin() {
  return (
    <AuthLayout
      title="TalentLens for teams"
      subtitle="Sign in with your work account. Accounts are created by your administrator."
      footer={
        <>
          Looking for your application?{' '}
          <Link to="/login" className="font-semibold text-accent-text underline-offset-2 hover:underline">
            Candidate sign-in
          </Link>
        </>
      }
    >
      <SignInForm door="staff" emailLabel="Work email" />
    </AuthLayout>
  );
}
