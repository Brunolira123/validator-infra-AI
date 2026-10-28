package br.com.vrinteriorpaulista.validator_infra.prospeccao.entity;

/**
 * Tipos de comércio de alimentos buscados no Google: uma textQuery por categoria.
 * Farmácia, restaurante e lanchonete ficam de fora de propósito (fora do público-alvo).
 */
public enum CategoriaProspeccao {

    SUPERMERCADO("supermercado"),
    HIPERMERCADO("hipermercado"),
    MERCADO("mercado mercearia mercadinho"),
    ATACADO("atacadista atacarejo"),
    HORTIFRUTI("hortifruti"),
    ACOUGUE("açougue"),
    PEIXARIA("peixaria"),
    PADARIA("padaria"),
    CONVENIENCIA("loja de conveniência"),
    EMPORIO("empório"),
    BEBIDAS("distribuidora de bebidas adega");

    private final String termoBusca;

    CategoriaProspeccao(String termoBusca) {
        this.termoBusca = termoBusca;
    }

    public String query(String cidade, String uf) {
        return termoBusca + " em " + cidade + " - " + uf;
    }
}
