/**
 * `/applications/:applicationId` — the application detail page the dashboard links to.
 *
 * <p>Re-exports `EmailResultPage`: since ADR-033 an `Application` only ever carries generated
 * email content (resume and cover-letter generation were removed), so the email view *is* the
 * application view. Kept as its own route rather than redirecting so every existing dashboard
 * link keeps working, and so the page has somewhere to grow if an Application ever holds more
 * than one output again.
 */
export { EmailResultPage as ApplicationDetailPage } from './EmailResultPage';
