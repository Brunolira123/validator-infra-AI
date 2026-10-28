package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.exception.LimiteExcedidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Trava de custo do Google Places no próprio app, além da cota diária do Google Cloud.
 * Só conta chamada que vai de fato ao Google (cache miss). Fica em memória: reiniciar o backend zera
 * a contagem do dia, e a cota do Google Cloud segue como trava final.
 */
@Component
public class CotaDiariaGoogle {

    static final String MENSAGEM_BUSCAS = "Limite diário de buscas atingido, tente amanhã";
    static final String MENSAGEM_DETALHES = "Limite diário de consultas de detalhes atingido";

    private final int limiteBuscas;
    private final int limiteDetalhes;
    private final int limiteFotos;
    private final Clock clock;

    private LocalDate dia;
    private int buscas;
    private int detalhes;
    private int fotos;

    @Autowired
    public CotaDiariaGoogle(@Value("${app.google.places.limite-diario.buscas:150}") int limiteBuscas,
                            @Value("${app.google.places.limite-diario.detalhes:150}") int limiteDetalhes,
                            @Value("${app.google.places.limite-diario.fotos:30}") int limiteFotos) {
        this(limiteBuscas, limiteDetalhes, limiteFotos, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    CotaDiariaGoogle(int limiteBuscas, int limiteDetalhes, int limiteFotos, Clock clock) {
        this.limiteBuscas = limiteBuscas;
        this.limiteDetalhes = limiteDetalhes;
        this.limiteFotos = limiteFotos;
        this.clock = clock;
        this.dia = LocalDate.now(clock);
    }

    /** Uma chamada de busca (uma categoria). Estourou = 429. */
    public synchronized void consumirBusca() {
        virarDia();
        if (buscas >= limiteBuscas) {
            throw new LimiteExcedidoException(MENSAGEM_BUSCAS);
        }
        buscas++;
    }

    /** Place Details de um prospect salvo. Estourou = o item da lista vem sem os dados do Google. */
    public synchronized void consumirDetalhes() {
        virarDia();
        if (detalhes >= limiteDetalhes) {
            throw new LimiteExcedidoException(MENSAGEM_DETALHES);
        }
        detalhes++;
    }

    /** Foto estourada não é erro para o usuário: o front mostra o placeholder. */
    public synchronized boolean tentarConsumirFoto() {
        virarDia();
        if (fotos >= limiteFotos) {
            return false;
        }
        fotos++;
        return true;
    }

    private void virarDia() {
        LocalDate hoje = LocalDate.now(clock);
        if (!hoje.equals(dia)) {
            dia = hoje;
            buscas = 0;
            detalhes = 0;
            fotos = 0;
        }
    }
}
