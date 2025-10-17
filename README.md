# JMPlayer - Leitor de Música Nativo para Android

JMPlayer é um leitor de música Android completo, construído do zero com foco numa arquitetura limpa (MVP), gestão de serviços em background (Media3) e funcionalidades modernas como reconhecimento de música (ACRCloud).

Este projeto foi desenvolvido como um estudo aprofundado das melhores práticas de desenvolvimento Android, demonstrando a gestão de ciclo de vida, concorrência (threads), bases de dados locais e integração com APIs externas.

---

## ✨ Funcionalidades Principais

* **🎧 Reprodução em Background:** A música continua a tocar mesmo quando a aplicação está minimizada ou o ecrã está desligado, graças ao uso de um `MediaSessionService`.
* **🗂️ Gestão de Playlists:**
    * Criação de novas playlists.
    * Adição de qualquer música a uma ou mais playlists.
    * Visualização e reprodução de playlists personalizadas.
    * Remoção de músicas de uma playlist.
    * Renomeação e exclusão de playlists.
* **🖼️ Exibição de Capas de Álbuns:** Carrega e exibe automaticamente as capas dos álbuns (Album Art) a partir do `MediaStore` do Android, usando a biblioteca `Glide` para performance e caching.
* **▶️ Controlo Total do Player:**
    * Controlos de Play/Pause, Próxima e Anterior.
    * Modos **Shuffle** (Aleatório) e **Repeat** (Off/All/One).
    * `SeekBar` interativa para avançar ou retroceder na música.
* **🔈 Gestão de Foco de Áudio:** Pausa automaticamente a música se outra aplicação (como o YouTube ou uma chamada telefónica) começar a reproduzir som, e retoma quando a outra aplicação para.
* **🔍 Busca Rápida:** Filtra a biblioteca de músicas ou a playlist atual em tempo real.
* **✨ Reconhecimento de Música:** Utiliza a API do **ACRCloud** para "ouvir" o som ambiente e identificar o nome da música e o artista, similar ao Shazam.

---

## 🏛️ Arquitetura e Stack Tecnológica

Este projeto foi construído com uma arquitetura **MVP (Model-View-Presenter)** para garantir uma separação clara de responsabilidades, facilitando a manutenção e os testes.

### Arquitetura (MVP)
* **Model:** Camada de dados. Responsável por buscar dados (do `MediaStore` ou da API `ACRCloud`) e gerir a base de dados (`Room`). Não conhece a UI.
* **View:** Camada de UI "passiva" (as `Activities`). Apenas mostra os dados recebidos do Presenter e captura os eventos do utilizador (cliques).
* **Presenter:** O "cérebro". Recebe eventos da View, processa-os (pedindo dados ao Model), e formata os dados para serem exibidos de volta na View.
* **Contract:** Uma interface (`SoundDeckContract`) que define a comunicação estrita entre a View e o Presenter.

### 🛠️ Stack Tecnológica

* **Linguagem:** Java
* **Arquitetura:** MVP
* **Core (Reprodução):**
    * **Androidx Media3 (ExoPlayer):** A biblioteca moderna de reprodução de média do Google, usada para toda a lógica de áudio.
    * **Androidx Media3 (Session):** Usada para criar o `MediaSessionService`, que gere a reprodução em background, a notificação de média e os controlos de ecrã de bloqueio.
* **Base de Dados (Model):**
    * **Androidx Room:** Biblioteca de persistência de dados (ORM) usada para guardar e gerir todas as playlists do utilizador.
* **Interface e Imagens (View):**
    * **Material Design 3:** Componentes de UI como `Toolbar`, `SearchView`, `AlertDialogs`, etc.
    * **ConstraintLayout:** Para a construção de layouts complexos e responsivos (como a tela principal).
    * **RecyclerView:** Para a exibição eficiente de listas de músicas e playlists.
    * **Glide:** Biblioteca de carregamento de imagens para carregar e armazenar em cache as capas dos álbuns.
* **APIs Externas:**
    * **ACRCloud SDK:** SDK (incluído manualmente) para a funcionalidade de reconhecimento de música.
* **Concorrência (Threading):**
    * **`ExecutorService`:** Usado para executar todas as operações de base de dados (Room) fora da thread principal (UI thread), evitando que a aplicação congele.
    * **`Handler` e `Looper`:** Usados para atualizar a `SeekBar` a cada segundo e para comunicar resultados das threads de background de volta para a Main Thread.

---

## 🚀 Como Configurar e Executar

Para compilar e executar este projeto, são necessários alguns passos de configuração manual devido às APIs externas.

### 1. Clonar o Repositório
```bash
git clone [https://github.com/JoseMaria1010/JMPlayer.git](https://github.com/JoseMaria1010/JMPlayer.git)
cd JMPlayer
