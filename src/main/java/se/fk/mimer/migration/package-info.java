/**
 * Innehåller migreringsmotorn för JSON-dokument.
 *
 * <p>Huvudkoncept:
 * <ul>
 *   <li>{@code Migration} – versionssteg (från -> till)</li>
 *   <li>{@code Rule} – JSONPath-selektor + mutator</li>
 *   <li>{@code AuditEntry} – logg av tillämpade förändringar</li>
 * </ul>
 *
 * <p>Trådsäkerhet: Engine är stateless och kan delas mellan trådar.
 * Mutering sker på {@link tools.jackson.databind.JsonNode}-träd som är per dokument.
 */
package se.fk.mimer.migration;