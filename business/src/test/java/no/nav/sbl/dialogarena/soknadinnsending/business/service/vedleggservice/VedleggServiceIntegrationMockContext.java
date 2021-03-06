package no.nav.sbl.dialogarena.soknadinnsending.business.service.vedleggservice;

import no.nav.sbl.dialogarena.sendsoknad.domain.XmlService;
import no.nav.sbl.dialogarena.sendsoknad.domain.message.TekstHenter;
import no.nav.sbl.dialogarena.soknadinnsending.business.WebSoknadConfig;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.SoknadRepository;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.vedlegg.VedleggRepository;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.vedlegg.VedleggRepositoryJdbc;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.FaktaService;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.soknadservice.*;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.fillager.FillagerService;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.henvendelse.HenvendelseService;
import no.nav.tjeneste.domene.brukerdialog.fillager.v1.FilLagerPortType;
import no.nav.tjeneste.domene.brukerdialog.sendsoknad.v1.SendSoknadPortType;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Named;
import javax.sql.DataSource;

import static org.mockito.Mockito.mock;

@Configuration
public class VedleggServiceIntegrationMockContext {

    @Bean
    public HenvendelseService hendvendelseService() {
        return mock(HenvendelseService.class);
    }

    @Bean
    public FillagerService fillagerService() {
        return mock(FillagerService.class);
    }

    @Bean
    public FaktaService faktaService() {
        return mock(FaktaService.class);
    }

    @Bean
    @Named("soknadInnsendingRepository")
    public SoknadRepository lokalDb() {
        return mock(SoknadRepository.class);
    }

    @Bean
    public WebSoknadConfig config() {
        return mock(WebSoknadConfig.class);
    }

    @Bean
    public ApplicationContext applicationContext() {
        return mock(ApplicationContext.class);
    }

    @Bean
    public TekstHenter tekstHenter() { return mock(TekstHenter.class); }

    @Bean
    public DataSource dataSource() {
        return mock(DataSource.class);
    }

    @Bean
    public XmlService xmlService() {
        return mock(XmlService.class);
    }

    @Bean
    @Named("fillagerEndpoint")
    public FilLagerPortType filLagerEndpoint() {
        return mock(FilLagerPortType.class);
    }

    @Bean
    @Named("fillagerSelftestEndpoint")
    public FilLagerPortType filLagerSelftestEndpoint() {
        return mock(FilLagerPortType.class);
    }

    @Bean
    @Named("sendSoknadEndpoint")
    public SendSoknadPortType sendSoknadEndpoint() {
        return mock(SendSoknadPortType.class);
    }

    @Bean
    public SoknadDataFletter soknadDataFletter() {
        return mock(SoknadDataFletter.class);
    }

    @Bean
    public AlternativRepresentasjonService alternativRepresentasjonService() {
        return mock(AlternativRepresentasjonService.class);
    }

    @Bean
    @Named("sendSoknadSelftestEndpoint")
    public SendSoknadPortType sendSoknadSelftestEndpoint() {
        return mock(SendSoknadPortType.class);
    }

    @Bean
    public SoknadService soknadService(){
        return mock(SoknadService.class);
    }

    @Bean
    public EttersendingService ettersendingService(){
        return mock(EttersendingService.class);
    }

    @Bean
    @Named("vedleggRepository")
    public VedleggRepository vedleggRepository() {
        return mock(VedleggRepositoryJdbc.class);
    }

    @Bean
    public SoknadMetricsService metricsService() {
        return mock(SoknadMetricsService.class);
    }
}
