import { Link } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';
import { SignInForm } from './SignInForm';

/** Candidate sign-in. Staff use /admins. */
export default function Login() {
  return (
    <AuthLayout
      title="Follow your application"
      subtitle="See where you are, when your interviews are, and ask to move one if you need to."
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-semibold text-accent-text underline-offset-2 hover:underline">
            Create an account
          </Link>
          <span className="mt-3 block text-[13px]">
            Work here?{' '}
            <Link to="/admins" className="underline-offset-2 hover:text-ink hover:underline">
              Staff sign-in
            </Link>
          </span>
        </>
      }
    >
      <SignInForm door="candidate" emailLabel="Email" />
    </AuthLayout>
  );
}
