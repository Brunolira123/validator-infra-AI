package br.com.vrinteriorpaulista.validator_infra.config;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mantém as restrições CHECK das colunas de enum em dia com o código.
 * <p>
 * O Hibernate cria {@code <tabela>_<coluna>_check} com os valores do enum quando cria a tabela, mas o
 * {@code ddl-auto=update} nunca atualiza essa restrição: um valor novo (ex.: o perfil DIRETORIA) é recusado
 * pelo banco de produção, criado antes dele. Roda antes do {@link DataSeeder}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RestricoesDeEnum implements ApplicationRunner {

    private final EntityManagerFactory emf;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public RestricoesDeEnum(EntityManagerFactory emf, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.emf = emf;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (EntityType<?> entidade : emf.getMetamodel().getEntities()) {
            Class<?> classe = entidade.getJavaType();
            Table tabela = classe.getAnnotation(Table.class);
            if (tabela == null) continue;

            for (Field campo : classe.getDeclaredFields()) {
                Enumerated enumerated = campo.getAnnotation(Enumerated.class);
                if (enumerated == null || enumerated.value() != EnumType.STRING || !campo.getType().isEnum()) continue;

                Column column = campo.getAnnotation(Column.class);
                String coluna = column != null && !column.name().isBlank() ? column.name() : snakeCase(campo.getName());
                List<String> valores = Arrays.stream(campo.getType().getEnumConstants())
                        .map(c -> ((Enum<?>) c).name())
                        .toList();
                sincronizar(tabela.schema(), tabela.name(), coluna, valores);
            }
        }
    }

    void sincronizar(String schema, String tabela, String coluna, List<String> valores) {
        String qualificada = schema.isBlank() ? tabela : schema + "." + tabela;
        String restricao = tabela + "_" + coluna + "_check";

        List<String> atual = jdbc.queryForList("""
                select pg_get_constraintdef(c.oid)
                from pg_constraint c
                join pg_class t on t.oid = c.conrelid
                join pg_namespace n on n.oid = t.relnamespace
                where c.conname = ? and t.relname = ? and n.nspname = ?
                """, String.class, restricao, tabela, schema.isBlank() ? "public" : schema);
        if (!atual.isEmpty() && valores.stream().allMatch(v -> atual.get(0).contains("'" + v + "'"))) {
            return;
        }

        String lista = valores.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", "));
        try {
            // Numa transação: se o add falhar (linha com valor que saiu do enum), o drop é desfeito
            tx.executeWithoutResult(s -> {
                jdbc.execute("alter table " + qualificada + " drop constraint if exists " + restricao);
                jdbc.execute("alter table " + qualificada + " add constraint " + restricao
                        + " check (" + coluna + " in (" + lista + "))");
            });
            log.info("Restrição {} atualizada: {}", restricao, lista);
        } catch (RuntimeException e) {
            log.error("Não foi possível atualizar a restrição {} ({}): a anterior foi mantida", restricao, e.getMessage());
        }
    }

    private static String snakeCase(String nome) {
        return nome.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
