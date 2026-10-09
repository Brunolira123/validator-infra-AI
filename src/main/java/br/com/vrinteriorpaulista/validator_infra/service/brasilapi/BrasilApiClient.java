package br.com.vrinteriorpaulista.validator_infra.service.brasilapi;

import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class BrasilApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String MENSAGEM_INDISPONIVEL = "Serviço de consulta indisponível. Tente novamente.";

    private final RestClient restClient;

    public BrasilApiClient(RestClient.Builder builder,
                           @Value("${app.brasilapi.base-url:https://brasilapi.com.br/api}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * @param cnpj apenas os 14 dígitos
     */
    public BrasilApiCnpjResponse consultarCnpj(String cnpj) {
        BrasilApiCnpjResponse resposta;
        try {
            resposta = restClient.get()
                    .uri("/cnpj/v1/{cnpj}", cnpj)
                    .retrieve()
                    .body(BrasilApiCnpjResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException("CNPJ não encontrado na Receita");
        } catch (HttpClientErrorException.BadRequest e) {
            throw new IllegalArgumentException("CNPJ inválido segundo a Receita");
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException(MENSAGEM_INDISPONIVEL, e);
        }

        if (resposta == null) {
            throw new ServicoIndisponivelException(MENSAGEM_INDISPONIVEL, null);
        }
        return resposta;
    }
}
