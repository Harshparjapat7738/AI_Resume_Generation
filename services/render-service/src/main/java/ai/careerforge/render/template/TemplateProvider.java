package ai.careerforge.render.template;

/**
 * Supplies template markup to the renderer without the renderer ever knowing where a template
 * actually lives. Every source of templates — built-in resources today
 * ({@link BuiltInTemplateProvider}), conceivably something else later — implements this and
 * nothing else; the renderer depends only on this interface, never on a resource path, a file
 * handle, or any storage detail.
 *
 * <p>This is what keeps render-service decoupled from {@code document-service}'s old shape:
 * that service coupled structural template analysis directly to storage and to a specific
 * template catalogue implementation. Here, "how a template is stored" and "how it is rendered"
 * are two different concerns joined only by this interface — swapping the implementation never
 * touches the renderer.
 */
public interface TemplateProvider {

    /**
     * Loads and structurally validates one template.
     *
     * @param key which built-in layout, for which document shape
     * @return the loaded, validated template
     * @throws TemplateNotFoundException       no template is registered for {@code key}, or it
     *                                          could not be read
     * @throws InvalidTemplateException        the template was read but failed its structural
     *                                          integrity check
     * @throws UnsupportedTemplateTypeException the template resolved to a markup kind this
     *                                          pipeline does not support
     */
    LoadedTemplate load(TemplateKey key);
}
