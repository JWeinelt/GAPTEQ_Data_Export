# ⚙️ GAPTEQ Data Exporter

[![Build](https://github.com/JWeinelt/GAPTEQ_Data_Export/actions/workflows/maven.yml/badge.svg)](https://github.com/JWeinelt/GAPTEQ_Data_Export/actions/workflows/maven.yml)

Welcome to the official repository for the GAPTEQ Exporter. This small software written in Java exports the files used in your repository to a format like SQL or CSV.

## 🏛️ How does it work?
1. 📥 Download the .jar file
2. 🔓 Open it with a double click or via command line: `java -jar gapteq_export.jar`
3. 📂 Select the folder of your repository  (using a copy is recommended!)
4. ⚙️ Edit your export options
5. 📤 Export!

## 💻 Requirements
- PC running Java 21 or newer
- Access to the GAPTEQ repository files

> [!NOTE]
> This tool was primary tested with GAPTEQ version 3.5. Other versions may work, as GAPTEQ uses the same JSON structure over all versions.

## 🎨 Screenshots
![MainGui](https://github.com/JWeinelt/GAPTEQ_Data_Export/blob/master/screenshots/gui1.png)

## 📜 License
This project is licensed under MIT license.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
