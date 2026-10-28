package mclauncher;

/** Arten von Inhalten, die man aus Modrinth in ein Profil installieren kann. */
public enum ContentType {
    MOD("Mods", "mods", "mod"),
    RESOURCEPACK(I18n.t("Ressourcenpakete"), "resourcepacks", "resourcepack"),
    SHADER(I18n.t("Shader"), "shaderpacks", "shader");

    public final String label;
    public final String folder;
    public final String modrinth;

    ContentType(String label, String folder, String modrinth) {
        this.label = label;
        this.folder = folder;
        this.modrinth = modrinth;
    }
}
