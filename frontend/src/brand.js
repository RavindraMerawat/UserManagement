/**
 * Everything that says the product's name, in one place.
 *
 * <p>The name appears on the login panel, the sidebar, the browser tab, the footer
 * and the About page. Keeping the strings here means renaming is one edit rather
 * than a search across the app.</p>
 *
 * <p>The server has its own copy for the Swagger title and the report email footer;
 * those are Java and cannot read this file. If the name changes, change it in both -
 * `OpenApiConfig` and `ReportExporter` on the backend.</p>
 */
export const BRAND = {
  name: 'Pandal Office Management',
  tagline: 'People · Service · Community',
  /** Two letters for the square mark, where a logo file would otherwise go. */
  mark: 'PO',
  headline: 'Together We Serve',
  lede: 'People · Service · Society',
  quote: '“Small acts of service make a big difference.”',
  version: 'v1.0.0',
}
